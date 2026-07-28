# High-Priority Remediation Plans

Plans for the two `priority: high` entries in the issue register: defensive decoding limits in
`CurseLinkOptionsPayload` and the runtime/world verification backlog for Todo and shared targeting.

**Rule for this document.** Every number below was read out of the tree and is followed by the file and
symbol it came from. Nothing here is estimated or remembered. Where no value exists in code yet, this
document says so explicitly and makes choosing it a step of the plan rather than quietly inventing one.
Point-in-time by design: delete it when both plans have landed.

---

## 1. Defensive decoding limits in `CurseLinkOptionsPayload`

### 1.1 What the code does today

Read from [`CurseLinkOptionsPayload.java`](../src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java):

- `read` takes the entry count straight off the wire: `int size = buffer.readVarInt();`.
- It then pre-allocates from that untrusted count: `List<Entry> entries = new ArrayList<>(size);`.
- It loops `for (int i = 0; i < size; i++)` and each iteration reads `buffer.readUUID()`, a second
  `buffer.readUUID()`, and `ResourceLocation.parse(buffer.readUtf())`.
- `write` is the mirror image: `writeVarInt(entries.size())`, then per entry two `writeUUID` calls and one
  `writeUtf(entry.techniqueId().toString())`.
- There is no bound on the count, no bound on the string, and no branch for an unparseable id — `parse`
  throws rather than returning `null`.

Direction of travel, from [`JujutsuNetworking.registerPayloads`](../src/main/java/jujutsu/mod/network/JujutsuNetworking.java):

- `PayloadTypeRegistry.playS2C().register(CurseLinkOptionsPayload.TYPE, CurseLinkOptionsPayload.STREAM_CODEC)`
  — this payload is **server to client**, so the decode runs on the player's machine and the threat model
  is a hostile or buggy server, not a hostile client.
- The reply travels the other way and is not exposed the same way:
  `PayloadTypeRegistry.playC2S().register(SelectCurseLinkPayload.TYPE, ...)`, and
  [`SelectCurseLinkPayload`](../src/main/java/jujutsu/mod/network/SelectCurseLinkPayload.java) decodes exactly
  one `buffer.readUUID()` — fixed width, no loop, nothing to cap.

What the decoded list is handed to:

- [`JujutsuClientNetworking`](../src/client/java/jujutsu/mod/client/network/JujutsuClientNetworking.java)
  registers the receiver as `context.client().setScreen(new CurseLinkSelectionScreen(payload.entries()))` —
  the packet opens a screen with no filtering in between.
- [`CurseLinkSelectionScreen.init`](../src/client/java/jujutsu/mod/client/gui/CurseLinkSelectionScreen.java)
  builds one `Button` per entry, with `bounds(width / 2 - 110, y, 220, 20)`, a first row at
  `height / 2 - entries.size() * 12`, and `y += 24` per row. So the row block is `24 * n` pixels tall and
  centred on the screen: every row past `height / 24` is drawn off-screen and is unreachable.
- The label calls `entry.sourceId().toString().substring(0, 8)`. That one is safe by construction —
  `UUID.toString()` is always 36 characters — but it is only safe because the field is a `UUID` and not the
  free-form string next to it.

What the producer side can legitimately contain, from
[`CurseLinkRegistry`](../src/main/java/jujutsu/mod/curse/CurseLinkRegistry.java):

- links live in `private final Map<UUID, CurseLink> links = new ConcurrentHashMap<>();`
- `createLink` unconditionally does `links.put(link.id(), link)` — no per-source or per-participant limit.
- `linksForParticipant` walks every value and returns `List.copyOf(result)` of all matches.

**Therefore there is no code-derived maximum entry count today.** Nothing in the registry caps how many
links one participant can appear in, so the cap has to be introduced as a new, named, justified constant.
This plan does not pretend to read it off an existing line.

### 1.2 Cost of the missing cap (arithmetic, from the shapes above)

- Minimum bytes per legitimate entry on the wire: `16` (first UUID) + `16` (second UUID) + `1` (the length
  prefix of an empty `writeUtf`) = **33 bytes**.
- A VarInt count occupies at most **5 bytes** and can declare up to `Integer.MAX_VALUE` = **2147483647**
  entries.
- Because `new ArrayList<>(size)` allocates its backing array *before* the first entry is read, those 5
  bytes buy an array of 2147483647 references with no matching payload behind them. The loop then fails on
  a truncated buffer — after the allocation, not before it.
- Even a fully-backed hostile packet is cheap for the sender: `n` rows on screen cost the sender only
  `33 * n` bytes, and every row past `height / 24` is not even reachable by the player.

### 1.3 Plan

**Step 1 — one home for the limits.** Add the two bounds as named constants next to the payload (a small
`CurseLinkWireLimits` holder, matching how `TodoProfile` and `ProjectJjkRitualPolicy` own their numbers
instead of scattering literals). Two values:

- `MAX_ENTRIES` — no existing code supplies this number. Choose it from the only code-side ceiling that
  exists, the screen layout above (`24` pixels per row, first row at `height / 2 - n * 12`), and record
  that arithmetic in the constant's Javadoc so the next reader can re-derive it instead of trusting it.
- `MAX_TECHNIQUE_ID_LENGTH` — do **not** hardcode a fresh literal. Bound it by what a `ResourceLocation`
  can legally be in the Minecraft version this mod targets (`minecraft_version=1.21.8` in
  [`gradle.properties`](../gradle.properties)); read that limit from the mapped source at implementation
  time and reference it, so the two can never drift apart.

**Step 2 — stop allocating from the declared count.** In `read`, validate `size` against
`0 <= size <= MAX_ENTRIES` and fail with a decoder exception before any allocation; then build a plain
`new ArrayList<>()` and let it grow with the entries that actually arrive. This is the whole fix for the
amplification described in 1.2: after it, 5 bytes buy 5 bytes of work.

**Step 3 — bound the string and define the unknown-id policy.** Replace `buffer.readUtf()` with the
length-bounded overload, and replace `ResourceLocation.parse` — which throws — with `tryParse`, which
returns `null`. Policy: skip the malformed entry, keep the rest, and emit one `JujutsuMod.LOGGER.debug`
line. This matches how the codebase already treats an unrecognised id on the receiving side —
`JujutsuNetworking.handleCharacterAbility` does `if (ability == null) { return; }` rather than throwing.

**Step 4 — make the writer respect the reader.** `write` must refuse to emit more than `MAX_ENTRIES`, so
the server can never produce a packet its own client rejects. The natural place is the call site that
builds the list from `linksForParticipant`, since that list is already sorted deterministically by
`Comparator.comparingLong(CurseLink::createdAt).thenComparing(CurseLink::id)` — truncation is therefore
stable rather than arbitrary.

**Step 5 — verification.** Add a pure codec test and register it as a `JavaExec` task in the `verification`
group, because [`build.gradle`](../build.gradle) wires `check` to
`tasks.withType(JavaExec).matching { it.group == 'verification' }`; anything registered that way is picked
up by `./gradlew qualityGate` with no further wiring. Cases:

1. Round trip of a valid payload preserves entry order, both UUIDs, and the technique id.
2. A declared count above `MAX_ENTRIES` is rejected, and rejected *before* an array of that size exists.
3. A negative declared count is rejected.
4. An over-long technique string is rejected.
5. A malformed technique id is skipped while the surrounding valid entries survive.
6. `write` of an over-long list cannot produce a buffer that `read` refuses.

Cases 2–4 must be shown red first: delete the limit, watch the test fail, and paste that failure into the
commit body. `AGENTS.md` requires exactly that for every new gate rule.

### 1.4 Acceptance mapping

| Issue acceptance criterion | Covered by |
| --- | --- |
| Cap on entry count, no allocation from the declared count | Steps 1–2, test cases 2–3 |
| Cap on decoded string length | Steps 1, 3, test case 4 |
| Defined policy for unknown ids | Step 3, test case 5 |
| Round trip of a valid payload | Test case 1 |
| Negative tests fail when the limit is removed | Step 5 red-run requirement |

---

## 2. Runtime/world verification backlog for Todo and shared targeting

### 2.1 The four commit paths, as they actually are

Read from the runtimes themselves:

| Path | Entry point | Gate | Cooldown started |
| --- | --- | --- | --- |
| Aimed swap | `TodoBoogieWoogieRuntime.tryCast` | `ability != CharacterAbility.PRIMARY` returns false | `CharacterAbility.PRIMARY`, `BOOGIE_WOOGIE_COOLDOWN_TICKS` |
| Feint | `TodoFakeClapRuntime.tryCast` | `ability != CharacterAbility.PRIMARY_SNEAK` returns false | `CharacterAbility.PRIMARY_SNEAK`, `FAKE_CLAP_COOLDOWN_TICKS` |
| Pair swap | `TodoPairSwapRuntime.tryCast` | `ability != CharacterAbility.SECONDARY` returns false | `CharacterAbility.SECONDARY`, `PAIR_SWAP_COOLDOWN_TICKS` |
| Marker swap | `TodoMarkerSwapRuntime.swapWithMark` | — | `CharacterAbility.PRIMARY`, `MARKER_SWAP_COOLDOWN_TICKS` |

One correction the plan depends on: **the marker swap has no `tryCast`.**
[`TodoMarkerSwapRuntime`](../src/main/java/jujutsu/mod/character/todo/TodoMarkerSwapRuntime.java) exposes
`hasMark` and `swapWithMark`, and it is reached only from inside the aimed cast — when
`aimed.mode() != TargetResolver.Mode.ENTITY || aimed.entityId().isEmpty()`,
`TodoBoogieWoogieRuntime.tryCast` calls `TodoMarkerSwapRuntime.hasMark(...)` and then
`swapWithMark(...)` before refusing. Any harness that tries to drive four `tryCast` methods will find
three; the fourth path must be reached through the aimed cast with no eligible target under the crosshair.

All casts share one gate:
[`TodoSwapGates.evaluate(boolean spectator, boolean alive, boolean unsafeTransport, boolean staggered, boolean handsEmpty)`](../src/main/java/jujutsu/mod/character/todo/TodoSwapGates.java)
— **5** boolean parameters, so the truth table is exactly **32** rows, returning one of the **3** `ClapGate`
values. It delegates transport state to
[`TodoTargetSafety.hasUnsafeTransportState`](../src/main/java/jujutsu/mod/character/todo/TodoTargetSafety.java),
which is `passenger || vehicle || leashed` — **3** booleans, **8** rows.

### 2.2 Placement search, exactly as sized by the code

`TodoBoogieWoogieRuntime.findSafeDestination` iterates
`for (int up = 0; up <= TodoProfile.SAFE_POSITION_UPWARD_BLOCKS; up++)` over
`HORIZONTAL_OFFSETS`. From [`TodoProfile`](../src/main/java/jujutsu/mod/character/todo/TodoProfile.java)
`SAFE_POSITION_UPWARD_BLOCKS = 3`, and the loop is inclusive, so that is **4** vertical steps.
`buildHorizontalOffsets()` returns, in order: `Vec3.ZERO`, four offsets at `half`, four at `radius`, four at
`diag` — **13** entries, where `radius = SAFE_POSITION_HORIZONTAL_RADIUS = 1.0`, `half = radius * 0.5 = 0.5`
and `diag = radius * 0.7 = 0.7`.

**4 × 13 = 52 candidate points are tested per body per cast**, in a fixed order, before the fallback.
That ordering is a behavioural contract: the first accepted candidate wins, so the test that matters is
*which* of the 52 is chosen, not merely that some safe point was found.

Strictness per path, read at the call sites:

- Aimed swap: `SOFT` for Todo's own arrival, `STRICT` for the target.
- Marker swap onto a landed mark: `STRICT`, single body.
- Marker swap onto a marked body: `STRICT` for both.
- Pair swap: `STRICT` for both bystanders.

`SOFT` differs from `STRICT` only in the last-resort branch
(`if (strictness == Strictness.SOFT && isInWorldDestination(level, entity, requested))`), and
`isPlaceableDestination` is `isInWorldDestination(...) && level.noBlockCollision(entity, boundingBoxAt(entity, candidate))`.
`isInWorldDestination` checks finiteness, `level.isInWorldBounds`, `hasChunk(x >> 4, z >> 4)` and
`getWorldBorder().isWithinBounds(box.inflate(TodoProfile.WORLD_BORDER_MARGIN))` with
`WORLD_BORDER_MARGIN = 0.05`.

### 2.3 Numbers a world test must assert against

All from `TodoProfile`, all verified in this pass:

| Constant | Value | Where it bites |
| --- | --- | --- |
| `BOOGIE_WOOGIE_RANGE` | `20.0` | `todo.distanceToSqr(target) > 20.0 * 20.0` refuses the aimed swap; `TodoPairSwapRuntime.inReach` uses the same square for both participants |
| `BOOGIE_WOOGIE_COOLDOWN_TICKS` | `60` | aimed swap |
| `FAKE_CLAP_COOLDOWN_TICKS` | `20` | feint, on its own slot |
| `PAIR_SWAP_COOLDOWN_TICKS` | `100` | pair swap, taken only on commit — `mark` takes none |
| `PAIR_SELECTION_TTL_TICKS` | `100` | `level.getGameTime() + 100` is the pending mark's deadline |
| `MARKER_SWAP_RANGE` | `32.0` | `todo.position().distanceToSqr(destination) > 32.0 * 32.0` |
| `MARKER_SWAP_COOLDOWN_TICKS` | `60` | both marker routes, via `finish` |
| `MARKER_BODY_MARK_TTL_TICKS` | `200` | body marks only; a landed mark has no clock |
| `ENTITY_MARK_COOLDOWN_TICKS` | `20` | marking a body by hand |
| `MARKER_FLIGHT_TICKS` | `60` | thrown marker lifetime |
| `MARKER_THROW_POWER` | `1.35f` | throw |
| `MARKER_SURFACE_OFFSET` | `0.15` | resting marker offset from the struck face |
| `SWAP_MOMENTUM_WINDOW_TICKS` | `24` | shorter than the `60`-tick swap cooldown, so two grants cannot overlap — that relationship is worth an assertion of its own |
| `SWAP_MOMENTUM_DAMAGE_MULTIPLIER` | `1.25` | attribute modifier |
| `SWAP_MOMENTUM_STAGGER_TICKS` | `8` | the actual payload of the window |
| `MELEE_DAMAGE_MULTIPLIER` | `1.50` | with the momentum multiplier: `1.0 → 1.5 → 1.875` for an empty fist |
| `ATTACK_SPEED_MULTIPLIER` | `0.85` | attribute modifier |
| `STAGGER_DURATION_MULTIPLIER` | `0.50` | incoming stagger |
| `BLACK_FLASH_CHANCE` | `0.10f` | melee bridge |
| `BLACK_FLASH_DAMAGE_MULTIPLIER` | `1.75f` | melee bridge |
| `BLACK_FLASH_STAGGER_TICKS` | `14` | melee bridge |
| `BOOGIE_WOOGIE_CUE_RADIUS` | `64.0` | every cue broadcast in `emitSwapImpact` |
| `BOOGIE_WOOGIE_MOVE_SOUND_DELAY_TICKS` | `1` | whoosh, at both ribbon ends |
| `BOOGIE_WOOGIE_IMPACT_SOUND_DELAY_TICKS` | `3` | one landing report at the arrival midpoint |

### 2.4 Shared targeting: what is already pure, and what is not

[`TargetResolver`](../src/main/java/jujutsu/mod/combat/TargetResolver.java) already separates the two
halves, which is what makes this backlog tractable:

- `resolve(level, owner, maxRange, eligible)` does the world work: one `level.clip(...)`, then
  `level.getEntities(owner, new AABB(origin, end).inflate(SEARCH_INFLATE), ...)` with
  `SEARCH_INFLATE = 2.5`, then `toCandidate` which clips `bounds.inflate(ENTITY_HITBOX_INFLATE)` with
  `ENTITY_HITBOX_INFLATE = 0.35` and sets `pierced` from a second clip against the *real* box.
- `resolveForTests(...)` is the pure ranking half and is already reachable without a world.

The ranking has **4** comparator keys in this order: `!pierced`, then `pierced ? hitDistance : angularOffset(...)`,
then `hitDistance`, then `entityId`. There are **3** epsilon filters at `1.0E-3`
(`hitDistance > 1.0E-3`, `<= maxRange + 1.0E-3`, `<= blockDistance + 1.0E-3`), a fourth `1.0E-3` slack in
`toCandidate`, and **2** degenerate-vector guards at `1.0E-5` (`angularOffset` returns `0.0`,
`safeDirection` falls back to `new Vec3(0.0, 0.0, 1.0)`).

**4** callers share it: `TodoBoogieWoogieRuntime`, `TodoPairSwapRuntime`, plus the two Nobara runtimes
(`NobaraHammerCombatRuntime`, `ProjectJjkNobaraRuntime` / `ProjectJjkRitualRuntime` in
`src/main/java/jujutsu/mod/character/nobara/projectjjk/`). Every one of them passes a different `maxRange`
— Todo's two paths pass `BOOGIE_WOOGIE_RANGE = 20.0`, and the ritual path is separately bounded by
`ProjectJjkRitualPolicy.MAX_RANGE = 64.0`.

What is uncovered is precisely the world half: nothing exercises `level.clip`, the `2.5`-inflated sweep,
or the `0.35` pad against real hitboxes.

### 2.5 Plan

**Phase A — pure coverage that needs no new infrastructure.** Cheapest first, and it retires real risk:

1. `TodoSwapGates.evaluate` — all 32 rows, asserting the exact `ClapGate` value, plus all 8 rows of
   `TodoTargetSafety.hasUnsafeTransportState`.
2. `TargetResolver.resolveForTests` — extend past the existing comparator cases to the four boundary
   conditions the epsilons define: a candidate at exactly `maxRange`, one just past it, one at exactly the
   block distance, and one behind the block.
3. A profile-invariant test: `SWAP_MOMENTUM_WINDOW_TICKS (24) < BOOGIE_WOOGIE_COOLDOWN_TICKS (60)` and
   `< MARKER_SWAP_COOLDOWN_TICKS (60)`. The comment in `TodoProfile` asserts this relationship in prose;
   nothing enforces it.
4. Extract the 52-point candidate ordering from `findSafeDestination` into a pure sequence generator and
   assert the exact order, so the *choice* among the 52 is testable without a level.

Each of these registers as another `JavaExec` task in the `verification` group and is therefore already
wired into `check` and `qualityGate`.

**Phase B — world coverage.** Only after Phase A, because Phase A shrinks what actually needs a world.
Requirements to settle first, in this order: the GameTest entrypoint declaration in `fabric.mod.json`, a
dedicated Gradle task, and structure templates. Then the scenarios, one per behaviour that Phase A cannot
reach:

- Aimed swap where the target's destination is inside geometry: the `STRICT` side must yield `null`, the
  `TodoSwapPlan.preflight` must return empty, and **nothing** moves — the record has only the two-argument
  factory, so an empty plan is the only cancel path.
- Aimed swap in mid-air: Todo's `SOFT` side takes the last-resort branch and the cast still commits.
- Target at exactly `20.0` and just beyond it.
- Marker swap onto a landed mark at exactly `32.0`; a landed mark survives its use, a body mark does not
  (`TodoSwapMarks.onUsed` owns that difference, `clear` is the unconditional teardown).
- The fallback ordering itself: with a live mark *and* an eligible target under the crosshair, the aimed
  target must win, because `hasMark` is only consulted after the `ENTITY` branch fails.
- Pair swap where the two participants are far apart but both within `20.0` of Todo — the runtime
  deliberately never measures participant-to-participant distance.
- Pair-swap cancel by aiming back at the mark: `first == aimed` clears the pending selection, returns
  `true`, and takes no cooldown.
- Pair-swap expiry at `100` ticks, and the `died` branch of `tickSelections`, which is the only one that
  messages the caster.
- A failed second placement: rollback restores both bodies and, when a restore fails, logs
  `"Todo {} rollback incomplete"`. The single-body marker route passes `null` as the second participant.
- `restoreMotionAndRotation` sets `entity.hurtMarked = true`; without it the client keeps its own velocity.

**Phase C — the manual pass.** The Nobara smoke items in the issue (hammer targeting, nail launch,
directional Hairpin) are graphics-bound and cannot be automated here. They belong on the client-smoke
checklist in [`BUILDING_IN_SANDBOX.md`](BUILDING_IN_SANDBOX.md), which already owns that checklist, with
the result recorded there rather than in a commit message.

### 2.6 Explicitly out of scope

As stated in the issue: PR #17, anything requiring `runClient` graphics, and replacing existing pure tests.
Phase A adds to them; it does not convert them.
