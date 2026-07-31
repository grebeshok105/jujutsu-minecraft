# Fix plan: issues #30, #31, #29, #20

Status: PR A implementation is landed in the active worktree for the #30/#31 pounce/follow and #29 shadow-pool fixes. Issue #20 remains plan-only and is intentionally untouched. Historical observations below describe the pre-fix state.

Execution order follows the issue priority: #30 and #31 as one investigation (split into two PRs if the diff grows), then #29, then #20.

## Revision 4 — focused PR #47 hardening

The active implementation now separates the 0.34 movement attribute from the explicit 1.0 navigation speed modifier used after an allowed pounce resume. Ordinary completion uses collision-resolved post-move velocity: airborne completion keeps only the shared damped horizontal component, grounded completion is zero, and invalid contact or cleanup remains zero. The first actual movement tick is modeled as elapsed tick 1, so an early `onGround` flag alone does not cancel it while real collision flags still do; swept impact still wins the same post-move tick. Shadow presentation now uses a dedicated `megumi_shadow_spot` resource, a one-in-ten neutral accent population, inherited lighting and dark dust for Sic/pounce with no bright teal ring. Issue #31 remains only partially addressed: the confirmed post-pounce resume defect is covered, while full follow/goal responsiveness remains in-game smoke scope. Issue #20 remains untouched.

## Revision 3 — PR A implementation

The diagnostic session confirmed that `setNoAi(true)` did not move the dog and that shared `debugTriangleFan` batching joined separate shadow pools. The active implementation now owns pounce movement with explicit gravity and `MoverType.SELF`, uses a swept target check with impact-before-landing precedence, captures pre-impact travel direction, allows damped exit motion only for valid contact, resumes navigation only from a pure command/target eligibility policy through direct `moveTo(target, speed)`, and renders each shadow pool from independent `debugQuads` sectors. The shadow mote keeps its existing sprite and lifetime but no longer uses saturated full-bright teal. The temporary `MEGUMI_DIAG` logging is removed before packaging. In-world smoke remains separate from `qualityGate`; the user performed the diagnostic smoke, while final post-fix gameplay confirmation is still pending.

## Revision 2 — review corrections applied

1. #20 no longer conflates malformed syntax with an unknown technique id; the unknown-id acceptance criterion is called out as blocked or scoped, and E2 can no longer be closed on syntax handling alone.
2. Decoding failures now have a per-failure policy matrix instead of one blanket drop-entry rule.
3. Knockback direction is captured immediately before `finishPounce()`, not at launch time.
4. Navigation resume is a narrow transition on the ordinary termination path, not a side effect added to the generic `finishPounce()`.

---

## Issue #30 — Sic pounce flight and impact (critical)

### What the code does today

Files read: `MegumiDivineDogEntity`, `MegumiSummonRuntime.tickPounce` / `resolvePounceImpact`, `MegumiPouncePolicy`, `MegumiProfile`, `MegumiDogPresentationPolicy`, `MegumiPouncePolicyTest`.

- `launchPounce` calls `setNoAi(true)`, `getNavigation().stop()`, sets a ballistic velocity, and sets `hurtMarked = true` (`MegumiDivineDogEntity.launchPounce`).
- Each tick while `pounceInFlight()`, `tickPounce` re-evaluates in-flight facts, then re-applies `steerVelocity`, overwriting horizontal velocity toward the target at full `POUNCE_HORIZONTAL_SPEED` (0.92) while preserving the current vertical component (`MegumiPouncePolicy.steerVelocity`).
- The flight ends via `flightAction(horizontalCollision, verticalCollision, onGround, elapsedTicks)`: FINISH on either collision flag, or on `elapsedTicks > 1 && onGround`; elapsed tick 1 is the first reachable post-launch movement tick and does not self-cancel on ground alone.
- Impact: `resolvePounceImpact` runs when `dog.getBoundingBox().inflate(0.30).intersects(target.getBoundingBox())`. It calls `dog.finishPounce()` first, then re-checks validity and applies one `hurtServer` for `DOG_ATTACK_DAMAGE + POUNCE_BONUS_DAMAGE`, then `target.knockback(POUNCE_KNOCKBACK, -direction.x, -direction.z)`, stagger, sound, `DOGS_POUNCE` cue.
- `finishPounce` resets pounce state and accepts the explicitly chosen exit velocity; generic cleanup still calls the zero-velocity overload and only calls `setNoAi(false)` when the dog is in `Phase.ACTIVE`.
- Post-move exit motion is selected by a pure policy from termination reason, grounded state and collision-resolved velocity. Knockback direction prefers that resolved horizontal motion, then `target.position() - dog.position()`, horizontal only, negated.

### Candidate root causes to prove or kill first (do not tune blindly)

1. **Air stall from continuous steering.** `steerVelocity` re-locks horizontal speed to 0.92 every tick but never touches vertical velocity except preserving it. With `setNoAi(true)` the wolf's own gravity handling is questionable to assume — the plan step is to instrument one pounce and log per-tick `getDeltaMovement()` and position. If vertical velocity decays toward 0 while horizontal stays 0.92, the dog hangs.
2. **Undershoot is structural.** Max horizontal reach is `0.92 * 16 ticks` of `POUNCE_TIMEOUT_TICKS` ~ 14.7 blocks of travel, but `canLaunch` gates distance to `[POUNCE_MIN_RANGE=3, POUNCE_MAX_RANGE=8]`. Undershoot therefore means the flight is being terminated early (collision flags) or vertical arc drops the dog onto the ground after the first movement tick. Distinguish by logging which `flightAction` branch ended the flight.
3. **Pre-attack twitch.** `assignSicTarget` calls `finishPounce()` then `super.setTarget(target)`. `MeleeAttackGoal(priority 2)` starts navigating toward the target while the pounce launcher in `tickPounce` waits for `pounceReady` and the distance band. The dog visibly approaches through melee navigation, then gets yanked into `setNoAi(true)` ballistic flight. The pre-pounce navigation-to-ballistic handoff is the twitch candidate.
4. **Unreliable impact.** The impact AABB test uses a 0.30 inflate against a fast-moving dog; between ticks the dog can tunnel through or past the target without the inflated boxes intersecting. Separately, `resolvePounceImpact` re-checks `dog.getTarget() != target` after `finishPounce()` — verify that nothing in `finishPounce` or a same-tick AI tick can clear the target before the check runs.
5. **Knockback direction sign.** `target.knockback(strength, -direction.x, -direction.z)` where `direction = target - dog`. Vanilla `knockback(strength, x, z)` pushes away from `(x, z)`. Verify the vanilla sign semantics against the current call **before** changing anything: the existing call may already be correct, and rewriting a correct call is a regression, not a fix. The existing test only pins that the call happens after accepted damage, so it will not catch a wrong direction either way.

### Fix steps

1. Add a temporary diagnostic build that logs, per dog per tick during pounce: phase, delta movement, collision flags, onGround, ending branch. One in-game session answers causes 1, 2, 3 above. Remove before merge.
2. Based on the log, fix flight in `MegumiPouncePolicy` (pure, already JUnit-covered):
   - Give vertical motion an explicit model (gravity per tick) instead of assuming entity physics under `setNoAi(true)`, or stop disabling AI and drive the leap through navigation instead. Pick one owner for pounce motion; today it is split between the entity physics loop and the policy.
   - Replace the 0.30 AABB poll with a swept check (segment from previous to current position vs target box) so fast flight cannot tunnel.
3. **Knockback direction — capture it immediately before `finishPounce()` zeroes movement, never at launch time.**
   - Launch-time direction is the wrong source: the pounce re-steers toward the target every tick, so if the target moves sideways during flight the launch vector and the true impact vector diverge. Storing the launch vector would produce a deterministic, testable, and wrong knockback.
   - Capture order of precedence, evaluated before motion state is cleared:
     1. horizontal component of `dog.getDeltaMovement()` when it is non-zero — this is the actual travel direction at contact;
     2. otherwise horizontal `target.position() - dog.position()`, computed **before** `finishPounce()` zeroes movement;
     3. otherwise the last non-zero steering direction retained from the flight.
   - Only after the vanilla-semantics check in cause 5 decide whether the sign passed to `target.knockback` changes at all. The capture fix and the sign fix are separate decisions; do not bundle them.
4. Extend `MegumiPouncePolicyTest` with the new pure rules (vertical model, swept-impact predicate, pre-impact direction precedence including each fallback). Keep the existing source-text impact-order assertions.
5. Red mutations: remove gravity model -> undershoot/stall test fails; revert swept check -> tunneling test fails; switch direction capture to launch time -> moving-target direction test fails; skip the delta-movement branch -> fallback precedence test fails.
6. In-game smoke per issue steps: both dogs, mid-range, open ground and obstacles; record each pounce path, recovery, and knockback direction. `qualityGate` alone does not close this issue.

---

## Issue #31 — follow and Sic responsiveness (same investigation as #30)

### What the code does today

- Goals: `FloatGoal(0)`, `MeleeAttackGoal(2, 1.0, true)`, `FollowOwnerGoal(6, 1.0, FOLLOW_START_DISTANCE=10, FOLLOW_STOP_DISTANCE=2)`, `LookAtPlayerGoal(8)`, `RandomLookAroundGoal(8)`; targets: `OwnerHurtByTargetGoal(1)`, `OwnerHurtTargetGoal(2)` (`registerGoals`).
- `DOG_MOVEMENT_SPEED = 0.34` (`MegumiProfile`).
- `setPresentationPhase` calls `setNoAi(!combatEnabled)` during MATERIALIZING/RECALLING and clears sic + target + navigation on those phases.
- Leash recovery teleports a dog farther than `LEASH_DISTANCE=32` every `LEASH_RETRY_TICKS=10` ticks via `MegumiGroundSafety.findLeashPosition`.
- After `finishPounce`, AI resumes only if phase is ACTIVE (`setNoAi(false)`), but nothing re-issues the sic target to the goal system beyond the pre-existing `super.setTarget` from `assignSicTarget`.
- `finishPounce()` is generic: it is reached from `assignSicTarget`, `clearSicCommand`, the in-flight `FINISH_POUNCE` action, the timeout path, and `resolvePounceImpact`.

### Candidate root causes

1. **Speed, not logic.** 0.34 is near vanilla wolf walk speed; 'sluggish follow' may be pure tuning. Verify before touching goals.
2. **FollowOwnerGoal distances.** Start at 10, stop at 2: dogs hang back up to 10 blocks, which reads as lag. Confirm expected feel, then tune in `MegumiProfile` only.
3. **Post-pounce AI gap.** `finishPounce` re-enables AI but leaves navigation stopped; `MeleeAttackGoal` must re-tick to reacquire the path. If the target moved during flight, there is a visible pause.
4. **Goal priority conflict.** `MeleeAttackGoal(2)` and `FollowOwnerGoal(6)` fight whenever the owner walks away mid-combat; `OwnerHurtTargetGoal(2)` can also retarget the dog away from the sic target. Check whether `wantsToAttack` and owner-hurt goals can override a commanded sic target — that would read as 'ignores the command'.

### Fix steps

1. Reuse the #30 diagnostic session: measure summon-to-first-move latency, follow catch-up time, sic-command-to-pounce-launch time. Numbers first, tuning second.
2. Tune only `MegumiProfile` constants that the measurements indict (`DOG_MOVEMENT_SPEED`, follow distances). Profile constants are already pinned by `MegumiProfileTest`; update the pins with the new values and a comment tying each to the measurement.
3. **Do not add a navigation restart to `finishPounce()`.** That method is generic motion-state cleanup shared by target assignment, command clearing, timeouts, cancellation and impact. A `navigation.moveTo` inside it would cause movement during cleanup, revival of a cancelled command, navigation after an aborted pounce, and motion during recall or a phase change.
   Add instead a narrow resume transition reached only from ordinary pounce termination:

   ```text
   accepted impact / ordinary pounce termination
     -> finish motion state (existing finishPounce, unchanged)
     -> if presentation phase == ACTIVE
     -> if the sic command is still current (sicTargetUuid unchanged since launch)
     -> if the target is still valid (alive, not removed, same level, still eligible)
     -> resume navigation toward that target
   ```

   Encode the four-condition predicate as a pure policy method with JUnit coverage, in the style of `MegumiTargetPolicy`; `finishPounce` keeps having no side effect beyond motion state.
4. If owner-hurt goals override sic commands, encode the precedence in a small pure policy (sic target wins until cleared) with JUnit coverage.
5. Red mutations: move the resume into `finishPounce` -> cleanup-path test fails (a cancelled or recalled dog must not navigate); drop any one of the four conditions -> its focused test fails.
6. Same smoke session as #30 covers this issue (issue steps overlap deliberately).

---

## Issue #29 — summon/recall reads blue-teal instead of black

### What the code does today (fully traced)

Three separate color owners feed the ground effect:

1. **World shadow pool** (`ShadowWorldEffects.renderMegumiShadowPool` after PR 8): `setColor(0, 0, 0, alpha)` on independent `RenderType.debugQuads()` sectors. Already true black; the prior shared triangle-fan batching was the geometry culprit.
2. **`MEGUMI_SHADOW_MOTE` particle** (`MegumiShadowMoteParticle`): 1-in-5 particles is an `accent` mote with `rCol=0.10, gCol=0.92, bCol=0.80` at `alpha=0.95` and `getLightColor() = 0xF000F0` (full-bright). Full-bright saturated teal at 95% alpha reads as the dominant color of the cloud even at 20% population. Summon fires a 14-mote burst plus a 10-mote ring; recall fires a 14-mote ring (`MegumiVfxRecipes`).
3. **`SHADOW_TEAL` / `SHADOW_DARK` dust** in `MegumiVfxRecipes`: `SHADOW_TEAL = 0x2F8F83` — used only in `sic` and `pounce` recipes, not summon/recall. Relevant only if the blue is also seen on Sic.

### Fix steps

1. Kill or desaturate the accent mote: set accent colors to near-black (keep at most a faint desaturated edge if a highlight is wanted), and drop the full-bright `getLightColor` override for accents. This is the primary fix candidate and touches one file.
2. `SHADOW_DARK = 0x102E2B` remains a restrained dark secondary accent; the bright `SHADOW_TEAL` ring is removed from Sic/pounce.
3. The dedicated `megumi_shadow_spot` resource, one-in-ten accent population and inherited lighting are pinned by `MegumiShadowPresentationTest`; visual blackness remains a smoke question.
4. Visual verification only: screenshots of summon, recall, sic, pounce at several times of day, before/after. There is no automated way to prove 'reads black'. Keep world-pool code untouched — it is already black and pinned by `ShadowWorldEffectsTest`.

---

## Issue #20 — bound `CurseLinkOptionsPayload` decoding

### What the code does today (fully traced)

- `CurseLinkOptionsPayload.read`: `int size = buffer.readVarInt(); new ArrayList<>(size);` then `size` iterations of `readUUID(), readUUID(), ResourceLocation.parse(buffer.readUtf())`. No cap anywhere (`readUtf()` defaults to 32767 chars per string).
- Client receiver: `JujutsuClientNetworking` does `setScreen(new CurseLinkSelectionScreen(payload.entries()))` — decoded size feeds UI construction directly.
- Sender side: `CurseLinkRegistry.linksForParticipant` returns an unbounded sorted list; `CurseLinkSelection.resolve` treats `size() == 1` as auto-ready and larger lists as needing selection. There is **no natural maximum** in current code — the entry cap must be chosen and documented as a defensive constant.
- There is **no canonical catalog of technique ids** in the project, and no real producer of curse links was found. This matches the analysis already recorded in PR #32.

### Malformed syntax and unknown id are different checks

`ResourceLocation.parse` rejects only syntactically invalid strings. A well-formed but meaningless id such as `evil:not_a_technique` parses successfully and would still reach the UI. Issue #20 lists as a separate acceptance criterion that a payload naming an unknown id must not produce a button, so **syntax handling alone does not satisfy that criterion and does not close E2**.

Pick one of these two honest outcomes and record it on the issue before writing code:

- **Option A (recommended now).** Implement bounds and malformed-syntax handling in this pass. Explicitly record unknown-id acceptance as BLOCKED until a canonical technique-id catalog exists, because there is nothing to validate against today. Issue #20 is then **partially** addressed and E2 stays open with a narrowed description.
- **Option B.** Introduce a supported-id registry or allowlist as part of #20. This requires deciding who owns the registry, where ids are declared, how the first real producer registers its id, and how the client validates against it. Larger scope; needs its own design note before implementation.

Do not label a syntactically broken id as the unknown-id case and close the issue on it.

### Failure policy matrix — one outcome per failure type

A single blanket drop-entry rule is wrong: continuing to read after a codec-level failure assumes the stream is still aligned, which is only true for some failures.

| Failure | Outcome | Why |
|---|---|---|
| `size < 0` or `size > MAX_ENTRIES` | reject the entire payload, before any allocation | an untrusted count must never size an allocation, and the stream position after a bad count is not trustworthy |
| decoded string longer than `MAX_TECHNIQUE_ID_LENGTH` | reject the entire payload | after a length failure the following bytes cannot be assumed to start a valid entry |
| syntactically invalid `ResourceLocation`, string already fully read | drop that entry, keep the rest, log once | the stream is still aligned because the string was consumed in full |
| well-formed but unknown technique id | drop that entry under the supported-id policy — only exists under Option B | stream is aligned; this is a semantic failure, not a codec failure |
| writer assembles more than `MAX_ENTRIES` | refuse to encode (throw); never silently truncate | truncation would hide a server-side bug and desync the UI from the registry |

### Fix steps

1. Record the Option A / Option B decision on issue #20 first; the rest of the work depends on it.
2. In `CurseLinkOptionsPayload`:
   - Add `MAX_ENTRIES` as a documented defensive constant (no natural maximum exists in code; state that explicitly beside it).
   - Add `MAX_TECHNIQUE_ID_LENGTH` and read strings through `buffer.readUtf(MAX_TECHNIQUE_ID_LENGTH)`.
   - Validate the count **before** constructing the list; do not pre-size the `ArrayList` from the untrusted value.
   - Implement the matrix above exactly, including the writer-side refusal.
3. Client-side defence in depth: `CurseLinkSelectionScreen` then receives at most `MAX_ENTRIES` entries by construction; document that rather than re-bounding the UI.
4. Negative codec tests (new JUnit under `src/test/java/jujutsu/mod/network/`), one per matrix row: over-cap count rejected without allocating per that count; over-length string rejected as a whole payload; malformed id dropped while the remaining entries survive; writer refusal above the cap; and a valid payload that round-trips byte-identically with the buffer fully consumed. Add the unknown-id case only under Option B.
5. Red mutations: raise or remove each bound, and swap any reject into a drop — the matching test must fail.
6. `docs/KNOWN_ISSUES.md` E2: under Option A, narrow the entry to the remaining unknown-id gap and keep it open; under Option B, close it with the chosen constants and policy.

---

## Shared verification

- `qualityGate --no-daemon --max-workers=1 --no-watch-fs` green after each PR.
- #30/#31: in-game smoke is mandatory (the issues came from in-game smoke; `qualityGate` cannot reproduce them).
- #29: before/after screenshots.
- #20: codec tests are the full verification; no in-game step needed.
- Documentation: MOC/SESSION/KNOWN_ISSUES updated per fix; E2 status follows the Option A / Option B decision.

## Out of scope

- No VFX Core architectural changes (freeze boundary from PR 9 holds; none of these fixes needs a new channel/packet/callback).
- No new GameTest source set; existing JUnit + JavaExec infrastructure suffices.
- No audit of other payloads beyond #20.
