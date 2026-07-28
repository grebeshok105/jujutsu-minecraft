# High-Priority Remediation Plans

Plans for the two `priority: high` entries in the issue register: defensive decoding limits on `CurseLinkOptionsPayload` (#20) and the runtime/world verification backlog for Todo and shared targeting (#21).

This document is a plan. It implements nothing. Do not close either issue on the strength of it.

## How to read the numbers here

Every figure below is annotated with the file and symbol it was read from. Where a value does **not** exist in the code, this document says so explicitly and makes choosing it a step of the plan. Two constants in §1.3 are deliberate new decisions, not readings; they are marked as such.

All relevant policy comes from [../AGENTS.md](../AGENTS.md). Two rules govern everything below:

- **New tests are JUnit 5.** `fabric-loader-junit` boots the loader for the test JVM. The existing `JavaExec` verification programs stay and migrate gradually; [BUILDING_IN_SANDBOX.md](BUILDING_IN_SANDBOX.md) owns the migration order. **No step in this plan adds a new `JavaExec` task.**
- **Every new gate rule ships with proof that it can fail.** Break the thing the rule guards, record the mutation and the failure message in the commit body, restore. A rule with no recorded red run is not a rule.

---

# 1. Issue #20 — defensive decoding limits on `CurseLinkOptionsPayload`

## 1.1 What the code does today

Source: [../src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java](../src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java)

The record is `Entry(UUID linkId, UUID sourceId, ResourceLocation techniqueId)` and the stream codec's `read` performs, in order:

1. `int size = buffer.readVarInt();`
2. `List<Entry> entries = new ArrayList<>(size);`
3. `for (int i = 0; i < size; i++)` reading `readUUID()`, `readUUID()`, `ResourceLocation.parse(buffer.readUtf())`

There is no bound on `size`, no bound on the `readUtf` length, and no handling for an id that parses but means nothing to this mod.

**The payload is server-to-client.** [../src/main/java/jujutsu/mod/network/JujutsuNetworking.java](../src/main/java/jujutsu/mod/network/JujutsuNetworking.java) registers it on `PayloadTypeRegistry.playS2C()`. The unbounded decode therefore runs on the player's machine, and the threat model is a hostile or buggy **server**, not a hostile client.

The reply travels the other way. [../src/main/java/jujutsu/mod/network/SelectCurseLinkPayload.java](../src/main/java/jujutsu/mod/network/SelectCurseLinkPayload.java) is registered on `playC2S()` and reads exactly one `readUUID()` — fixed width, no loop, nothing to cap. **Do not spend work there.**

## 1.2 The three distinct defects

### Defect A — allocation sized by attacker-controlled input

`new ArrayList<>(size)` is executed before a single entry byte is read, so an attacker-controlled count causes an oversized allocation attempt before payload availability is validated. How that failure surfaces is not fixed: it may throw `OutOfMemoryError` on the allocation itself, or succeed and then fail inside the loop on a truncated buffer. **Do not write a specific crash scenario into the fix's test names** — assert the rejection, not the JVM's reaction to the unfixed code.

Sizing arithmetic, for scale rather than for a promise: a VarInt occupies at most 5 bytes and can express counts up to `2147483647`, while an entry on the wire costs two 16-byte UUIDs plus a length-prefixed string. **33 bytes is the smallest possible malformed entry before validation** — two UUIDs plus the single length byte of an empty string. It is *not* the size of a legitimate entry, because an empty string is not a serialized `ResourceLocation`.

### Defect B — unbounded string read

`buffer.readUtf()` is called with no explicit maximum. This needs a cap, and §1.3 Step 1 explains why the cap has to be invented rather than looked up.

### Defect C — unknown technique ids reach the UI

This is the defect most easily mis-solved. `ResourceLocation.tryParse` rejects **syntactically invalid** strings only. A well-formed but meaningless id such as `evil:not_a_technique` parses successfully and `ResourceLocation` cannot say whether anything is bound to it.

What happens next is in [../src/client/java/jujutsu/mod/client/gui/CurseLinkSelectionScreen.java](../src/client/java/jujutsu/mod/client/gui/CurseLinkSelectionScreen.java): `init()` creates one button per entry with no validation, labelling each with `entry.techniqueId().toString()` plus the first 8 characters of `entry.sourceId().toString()`. So a parse-level fix leaves the issue's actual requirement — an unknown id must not produce a button — unmet. **Syntax validation and existence validation are two separate steps and the plan must keep them separate.**

### What is *not* broken

The server side of the reply already validates properly. `SelfResonanceRuntime.select(ServerPlayer, UUID)` in [../src/main/java/jujutsu/mod/character/nobara/projectjjk/SelfResonanceRuntime.java](../src/main/java/jujutsu/mod/character/nobara/projectjjk/SelfResonanceRuntime.java) refuses unless the link exists in the registry, the player is a participant, and that player has at least 2 links. A fabricated button therefore cannot select a link the player does not own — the damage from Defect C is a misleading and spoofable UI, not privilege escalation. Say this plainly in the fix so nobody over-scopes it.

## 1.3 Plan

### Step 1 — introduce two named limits, and be honest that both are new decisions

Neither limit can be read out of existing code or out of Minecraft.

**`MAX_ENTRIES` cannot be derived from anything that exists today.** [../src/main/java/jujutsu/mod/curse/CurseLinkRegistry.java](../src/main/java/jujutsu/mod/curse/CurseLinkRegistry.java) stores links in an unbounded `ConcurrentHashMap`, `createLink` puts without any ceiling check, and `linksForParticipant` returns every match as a `List.copyOf`. There is no product maximum anywhere in the tree.

It also **cannot be derived from the screen height**, and an earlier draft of this document was wrong to try. `CurseLinkSelectionScreen` lays rows out from `height / 2 - entries.size() * 12` at `y += 24`, but `height` is a runtime value that depends on the client's resolution and GUI scale, so it cannot produce one fixed protocol constant. What the layout code *does* establish is that the screen creates one button per entry and has no scrolling or pagination, so an oversized list overflows the visible area at both ends. That is evidence the UI needs a small bound — not a formula for it.

So: choose `MAX_ENTRIES` as an explicit protocol/product constant, in one place, with the rationale in a comment. Then either guarantee the UI never receives more than that small number, or give the screen scrolling later; record which.

**`MAX_TECHNIQUE_ID_LENGTH` is likewise a project constant.** Minecraft 1.21.8's `ResourceLocation` defines the permitted character set, `parse`, `tryParse` and codecs, but exposes no maximum identifier length. An earlier draft instructed the implementer to "read the existing 1.21.8 limit and reference it" — that step is not performable and has been removed. Pick a deliberately conservative wire-security bound, and justify it in the comment against the namespace and path lengths this mod actually uses.

Both constants live next to each other, in one file, referenced by both `read` and `write`.

### Step 2 — validate before allocating

Read the count, range-check it against `[0, MAX_ENTRIES]`, and throw `DecoderException` on violation. Only then create the list, and **do not pre-size it from the wire value** — a bounded count makes pre-sizing safe but pointless, and not pre-sizing keeps the dangerous pattern out of the file for the next reader.

### Step 3 — syntax, then existence, as two steps

1. `readUtf(MAX_TECHNIQUE_ID_LENGTH)`, so an overlong string fails at the buffer.
2. `ResourceLocation.tryParse` instead of `parse`; a `null` result means malformed syntax.
3. **Separately**, check the parsed id against the set of technique ids this build actually supports, and drop entries that fail.

Step 3.3 has a prerequisite the implementer must resolve first: **no such set exists in the tree, and no producer of curse links was found either.** `CurseLinkRegistry.createLink` has no caller in any file read for this plan — the network package, the `curse` package, `SelfResonanceRuntime`, and the four other ProjectJJK runtimes. `SelfResonanceRuntime` only ever *reads* links. Before implementing, confirm this with `codegraph explore` or a repository-wide search; if it holds, then the technique ids carried by this payload are populated only by tests, and the allowlist must be defined together with the first real producer. Until then the allowlist is a single explicit set with a comment saying so.

For a dropped entry, log at debug and skip it — the same shape as the existing `if (ability == null) return;` guard on the ability path. Do not fail the whole packet for one bad entry unless the review in §1.5 decides otherwise.

### Step 4 — one writer policy: refuse

`write` must **refuse** to serialize a list longer than `MAX_ENTRIES` or an id longer than `MAX_TECHNIQUE_ID_LENGTH`, by throwing. A previous draft also suggested truncating at the call site; that contradiction is resolved here in favour of refusing only. **No silent truncation anywhere** — a player who quietly loses half their link options has no way to discover why.

The consequence belongs to the producer: `SelfResonanceRuntime.tryCast` builds the entry list by streaming `linksForParticipant(...)`, so if that can exceed the cap, the *product* answer (cap the number of links, or paginate the screen) must be decided there and recorded — not papered over in the codec.

Symmetry matters for its own sake: the server must not be able to produce a packet its own reader would reject.

### Step 5 — JUnit 5 tests, with a recorded red run

One new JUnit 5 test class, no new gradle task; `check` already runs the JUnit suite. Cases:

- round trip of a valid payload preserves every entry, in order
- a declared count above `MAX_ENTRIES` is rejected **before** a large allocation
- a negative VarInt count is rejected
- an over-length technique string is rejected
- a syntactically malformed id follows the chosen policy
- a syntactically valid but **unknown** id is dropped, while a known id in the same payload survives
- `write` refuses an oversized list rather than truncating it

The last three are the ones the previous draft did not cover. Then remove each new limit in turn, confirm the matching test goes red, restore, and paste the failure messages into the commit body.

## 1.4 Acceptance mapping for #20

| Issue requirement | Closed by |
|---|---|
| Cap entry count without allocating from the declared count | Step 1 (`MAX_ENTRIES`) + Step 2 |
| Cap technique-id string length | Step 1 (`MAX_TECHNIQUE_ID_LENGTH`) + Step 3.1 |
| Unknown ids must not produce a button | Step 3.3 **only** — parsing in Step 3.2 does not close this |
| Round trip of a valid payload | Step 5 |
| Negative codec tests that go red when the limit is removed | Step 5, red run recorded in the commit body |

## 1.5 Decide before implementing

1. Malformed-syntax policy: drop the entry, or reject the packet.
2. The two constant values, with written rationale.
3. Whether the product answer to "too many links" is a link cap, screen pagination, or both.

---

# 2. Issue #21 — runtime/world verification backlog

## 2.1 The gap, stated exactly

[../AGENTS.md](../AGENTS.md) already concedes it: a green `./gradlew qualityGate` proves the shape of the code, the contracts between vessels, and pure logic reachable without a world. Nothing in the suite constructs a `ServerLevel`, so no automated check casts an ability or moves a body. This plan does not restate that debt; it splits it into work that can start now and work that needs new infrastructure.

## 2.2 Correction to the issue text

The issue says there are four `tryCast` entry points. **There are three**, all verified in source:

| Entry point | Ability gate |
|---|---|
| `TodoBoogieWoogieRuntime.tryCast(ServerPlayer, CharacterAbility, boolean)` | `PRIMARY` |
| `TodoFakeClapRuntime.tryCast(...)` | `PRIMARY_SNEAK` |
| `TodoPairSwapRuntime.tryCast(...)` | `SECONDARY` |

`TodoMarkerSwapRuntime` has **no** `tryCast`. It exposes `hasMark(...)` and `swapWithMark(...)`, reached only from inside `TodoBoogieWoogieRuntime.tryCast` when the aimed resolve does not return `Mode.ENTITY` and a live mark exists. A harness that looks for a fourth `tryCast` will not find one; the fourth route is entered through the first.

## 2.3 Facts a harness must assert against

### Placement search — exactly 52 candidates per body

In [../src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java](../src/main/java/jujutsu/mod/character/todo/TodoBoogieWoogieRuntime.java), `findSafeDestination` iterates `for (int up = 0; up <= TodoProfile.SAFE_POSITION_UPWARD_BLOCKS; up++)` — and `SAFE_POSITION_UPWARD_BLOCKS = 3`, so 4 vertical steps — over `HORIZONTAL_OFFSETS`, built by `buildHorizontalOffsets()` as **13** entries: `Vec3.ZERO`, four at `half = radius * 0.5`, four at `radius`, four diagonals at `diag = radius * 0.7`, where `radius = SAFE_POSITION_HORIZONTAL_RADIUS = 1.0`. That is **52 candidate points per body per cast**, in a fixed order, and the first acceptable one wins — so *which* candidate wins is the interesting assertion, not merely that one was found.

`SOFT` adds a final fallback to the exact requested point, skipping `noBlockCollision`; `STRICT` does not and cancels instead.

### Strictness per body

| Route | Todo | Other body |
|---|---|---|
| Aimed swap | `SOFT` | `STRICT` |
| Marker swap onto a position | — | `STRICT`, one body |
| Marker swap onto a marked body | `STRICT` | `STRICT` |
| Pair swap | not moved | `STRICT` both |

### Pure surfaces that are already sized

- `TodoSwapGates.evaluate(boolean, boolean, boolean, boolean, boolean)` — 5 booleans, so **32 rows** map onto `ClapGate {ALLOWED, UNAVAILABLE, HANDS_FULL}`.
- `TodoTargetSafety.hasUnsafeTransportState(passenger, vehicle, leashed)` — 3 booleans, **8 rows**, returning their disjunction.
- `TargetResolver.resolveForTests(origin, look, maxRange, blockCandidate, entityCandidates, ownerEntityId)` — the ranking half with no world: a 4-key comparator (`!pierced`, then `pierced ? hitDistance : angularOffset`, then `hitDistance`, then `entityId`), three filters at `1.0E-3`, and two degenerate-vector guards at `1.0E-5`. The world half uses `ENTITY_HITBOX_INFLATE = 0.35` and `SEARCH_INFLATE = 2.5`.

### `TargetResolver` consumers — 5 classes, 6 call sites

Counted as call sites, enumerated, since an earlier draft mixed the two units:

| Class | Call sites | Range constant |
|---|---:|---|
| `TodoBoogieWoogieRuntime` | 1 | `TodoProfile.BOOGIE_WOOGIE_RANGE` |
| `TodoPairSwapRuntime` | 1 | `TodoProfile.BOOGIE_WOOGIE_RANGE` |
| `NobaraHammerCombatRuntime` | 1 | `ProjectJjkNobaraProfile.HAMMER_MELEE_RANGE` |
| `ProjectJjkNobaraRuntime` | 1 | `ProjectJjkNobaraProfile.TARGET_RANGE` |
| `ProjectJjkRitualRuntime` | 2 | `ProjectJjkNobaraProfile.HAIRPIN_ENLARGE_RANGE` |

The two Todo call sites use the four-argument overload with an eligibility predicate; the other four use the three-argument overload. `ProjectJjkStrawDollRuntime` does not use the resolver at all.

### An invariant currently asserted only in prose

`TodoProfile` explains that a momentum window cannot span two grants. That holds today only because `SWAP_MOMENTUM_WINDOW_TICKS = 24` is below both `BOOGIE_WOOGIE_COOLDOWN_TICKS = 60` and `MARKER_SWAP_COOLDOWN_TICKS = 60`. Nothing enforces the relationship.

## 2.4 Phase A — pure coverage, JUnit 5, startable today

No new infrastructure and no new gradle task; `check` already runs the JUnit suite.

1. Full 32-row table for `TodoSwapGates.evaluate`, and the 8-row table for `TodoTargetSafety.hasUnsafeTransportState`.
2. Ranking cases through `resolveForTests`: pierced beats non-pierced, tie-break order, each epsilon boundary from both sides, the degenerate-direction guard.
3. The momentum invariant above, asserted against the constants rather than restated in a comment.
4. Offset-order coverage for `buildHorizontalOffsets()` — that the sequence is stable and that `Vec3.ZERO` is first, since first-match-wins makes the order load-bearing.

Each new assertion gets a recorded red run.

## 2.5 Phase B — in-world coverage (infrastructure does not exist yet)

GameTest is **not** currently configured in this repository: [../build.gradle](../build.gradle) has no GameTest task and `fabric.mod.json` declares no GameTest entrypoint. Phase B is therefore a proposal to add that infrastructure, and must not be described as an existing configuration.

Scenarios, once a `ServerLevel` is reachable:

- geometry: which of the 52 candidates wins, per strictness, against a wall, a ceiling, water, and open air
- range boundaries: `distanceToSqr` against `BOOGIE_WOOGIE_RANGE = 20.0`, and the marker path against `MARKER_SWAP_RANGE = 32.0`, tested on both sides of the boundary
- routing: an eligible aimed target takes priority over a live mark; the mark route is entered only when the aimed resolve is not `Mode.ENTITY`
- pair swap: mark, cancel by re-aiming at the mark, expiry at `PAIR_SELECTION_TTL_TICKS = 100`, participant lost
- rollback: a failed second placement restores both bodies, and a failed restore emits the error log that is the only evidence of a body left off-plan
- velocity and rotation restored after a completed swap, including the `hurtMarked` path that tells the client its motion

## 2.6 Phase C — manual smoke

Whatever Phase B does not reach stays a documented manual checklist owned by [BUILDING_IN_SANDBOX.md](BUILDING_IN_SANDBOX.md). Explicitly out of scope for this issue: PR #17, anything visual behind `runClient`, and rewriting existing pure tests that already pass.

---

# 3. Sequencing

1. #20 Steps 1–5 — small, self-contained, and it is the one with a security dimension.
2. #21 Phase A — no infrastructure, immediate coverage gain.
3. #21 Phase B — needs a product decision on whether GameTest enters the build at all.

Run `./gradlew qualityGate` before any handoff. Nothing in this document may be called done, fixed or verified without a green run of exactly that command.
