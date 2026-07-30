# Fix plan: issues #30, #31, #29, #20

Status: PLAN ONLY. This PR changes no code. Every claim below was read off the source at base `11b4d5ae5f3871ef77a58f55533e700fd68d0c27`.

Execution order follows the issue priority: #30 and #31 as one investigation (split into two PRs if the diff grows), then #29, then #20.

---

## Issue #30 — Sic pounce flight and impact (critical)

### What the code does today

Files read: `MegumiDivineDogEntity`, `MegumiSummonRuntime.tickPounce` / `resolvePounceImpact`, `MegumiPouncePolicy`, `MegumiProfile`, `MegumiDogPresentationPolicy`, `MegumiPouncePolicyTest`.

- `launchPounce` calls `setNoAi(true)`, `getNavigation().stop()`, sets a ballistic velocity, and sets `hurtMarked = true` (`MegumiDivineDogEntity.launchPounce`).
- Each tick while `pounceInFlight()`, `tickPounce` re-evaluates in-flight facts, then re-applies `steerVelocity`, overwriting horizontal velocity toward the target at full `POUNCE_HORIZONTAL_SPEED` (0.92) while preserving the current vertical component (`MegumiPouncePolicy.steerVelocity`).
- The flight ends via `flightAction(horizontalCollision, verticalCollision, onGround, elapsedTicks)`: FINISH on either collision flag, or on `elapsedTicks > 0 && onGround`.
- Impact: `resolvePounceImpact` runs when `dog.getBoundingBox().inflate(0.30).intersects(target.getBoundingBox())`. It calls `dog.finishPounce()` first, then re-checks validity and applies one `hurtServer` for `DOG_ATTACK_DAMAGE + POUNCE_BONUS_DAMAGE`, then `target.knockback(POUNCE_KNOCKBACK, -direction.x, -direction.z)`, stagger, sound, `DOGS_POUNCE` cue.
- `finishPounce` zeroes delta movement, resets fall distance, and only calls `setNoAi(false)` when the dog is in `Phase.ACTIVE`.
- Knockback direction is computed **after** `finishPounce()` as `target.position() - dog.position()`, horizontal only, negated.

### Candidate root causes to prove or kill first (do not tune blindly)

1. **Air stall from continuous steering.** `steerVelocity` re-locks horizontal speed to 0.92 every tick but never touches vertical velocity except preserving it. With `setNoAi(true)` the wolf's own gravity handling is questionable to assume — the plan step is to instrument one pounce and log per-tick `getDeltaMovement()` and position. If vertical velocity decays toward 0 while horizontal stays 0.92, the dog hangs.
2. **Undershoot is structural.** Max horizontal reach is `0.92 * 16 ticks` of `POUNCE_TIMEOUT_TICKS` ~ 14.7 blocks of travel, but `canLaunch` gates distance to `[POUNCE_MIN_RANGE=3, POUNCE_MAX_RANGE=8]`. Undershoot therefore means the flight is being terminated early (collision flags) or vertical arc drops the dog onto the ground before arrival (`elapsedTicks > 0 && onGround` fires). Distinguish by logging which `flightAction` branch ended the flight.
3. **Pre-attack twitch.** `assignSicTarget` calls `finishPounce()` then `super.setTarget(target)`. `MeleeAttackGoal(priority 2)` starts navigating toward the target while the pounce launcher in `tickPounce` waits for `pounceReady` and the distance band. The dog visibly approaches through melee navigation, then gets yanked into `setNoAi(true)` ballistic flight. The pre-pounce navigation-to-ballistic handoff is the twitch candidate.
4. **Unreliable impact.** The impact AABB test uses a 0.30 inflate against a fast-moving dog; between ticks the dog can tunnel through or past the target without the inflated boxes intersecting. Separately, `resolvePounceImpact` re-checks `dog.getTarget() != target` after `finishPounce()` — verify that nothing in `finishPounce` or a same-tick AI tick can clear the target before the check runs.
5. **Knockback direction sign.** `target.knockback(strength, -direction.x, -direction.z)` where `direction = target - dog`. Vanilla `knockback(strength, x, z)` pushes away from `(x, z)`. Passing the negated dog-to-target vector throws the target *toward* the dog's approach line — verify against vanilla semantics before changing; the test only pins that the call exists after accepted damage.

### Fix steps

1. Add a temporary diagnostic build that logs, per dog per tick during pounce: phase, delta movement, collision flags, onGround, ending branch. One in-game session answers causes 1, 2, 3 above. Remove before merge.
2. Based on the log, fix flight in `MegumiPouncePolicy` (pure, already JUnit-covered):
   - Give vertical motion an explicit model (gravity per tick) instead of assuming entity physics under `setNoAi(true)`, or stop disabling AI and drive the leap through navigation instead. Pick one owner for pounce motion; today it is split between the entity physics loop and the policy.
   - Replace the 0.30 AABB poll with a swept check (segment from previous to current position vs target box) so fast flight cannot tunnel.
3. Fix the knockback vector only after step-1 evidence: capture the approach direction at `launchPounce` time and store it, instead of recomputing after `finishPounce()` from positions that may already overlap.
4. Extend `MegumiPouncePolicyTest` with the new pure rules (vertical model, swept-impact predicate, stored knockback direction). Keep the existing source-text impact-order assertions.
5. Red mutations: remove gravity model -> undershoot/stall test fails; revert swept check -> tunneling test fails; recompute knockback from post-finish positions -> direction test fails.
6. In-game smoke per issue steps: both dogs, mid-range, open ground and obstacles; record each pounce path, recovery, and knockback direction. `qualityGate` alone does not close this issue.

---

## Issue #31 — follow and Sic responsiveness (same investigation as #30)

### What the code does today

- Goals: `FloatGoal(0)`, `MeleeAttackGoal(2, 1.0, true)`, `FollowOwnerGoal(6, 1.0, FOLLOW_START_DISTANCE=10, FOLLOW_STOP_DISTANCE=2)`, `LookAtPlayerGoal(8)`, `RandomLookAroundGoal(8)`; targets: `OwnerHurtByTargetGoal(1)`, `OwnerHurtTargetGoal(2)` (`registerGoals`).
- `DOG_MOVEMENT_SPEED = 0.34` (`MegumiProfile`).
- `setPresentationPhase` calls `setNoAi(!combatEnabled)` during MATERIALIZING/RECALLING and clears sic + target + navigation on those phases.
- Leash recovery teleports a dog farther than `LEASH_DISTANCE=32` every `LEASH_RETRY_TICKS=10` ticks via `MegumiGroundSafety.findLeashPosition`.
- After `finishPounce`, AI resumes only if phase is ACTIVE (`setNoAi(false)`), but nothing re-issues the sic target to the goal system beyond the pre-existing `super.setTarget` from `assignSicTarget`.

### Candidate root causes

1. **Speed, not logic.** 0.34 is near vanilla wolf walk speed; 'sluggish follow' may be pure tuning. Verify before touching goals.
2. **FollowOwnerGoal distances.** Start at 10, stop at 2: dogs hang back up to 10 blocks, which reads as lag. Confirm expected feel, then tune in `MegumiProfile` only.
3. **Post-pounce AI gap.** `finishPounce` re-enables AI but leaves navigation stopped; `MeleeAttackGoal` must re-tick to reacquire the path. If the target moved during flight, there is a visible pause. Same for `clearSicCommand` paths.
4. **Goal priority conflict.** `MeleeAttackGoal(2)` and `FollowOwnerGoal(6)` fight whenever the owner walks away mid-combat; `OwnerHurtTargetGoal(2)` can also retarget the dog away from the sic target. Check whether `wantsToAttack` and owner-hurt goals can override a commanded sic target — that would read as 'ignores the command'.

### Fix steps

1. Reuse the #30 diagnostic session: measure summon-to-first-move latency, follow catch-up time, sic-command-to-pounce-launch time. Numbers first, tuning second.
2. Tune only `MegumiProfile` constants that the measurements indict (`DOG_MOVEMENT_SPEED`, follow distances). Profile constants are already pinned by `MegumiProfileTest`; update the pins with the new values and a comment tying each to the measurement.
3. If post-pounce re-targeting is the stall, restart navigation toward the sic target in `finishPounce` (or explicitly `getNavigation().moveTo(target, 1.0)` on resume) rather than waiting for the next goal tick.
4. If owner-hurt goals override sic commands, encode the precedence in a small pure policy (sic target wins until cleared) with JUnit coverage, following the existing `MegumiTargetPolicy` pattern.
5. Same smoke session as #30 covers this issue (issue steps overlap deliberately).

---

## Issue #29 — summon/recall reads blue-teal instead of black

### What the code does today (fully traced)

Three separate color owners feed the ground effect:

1. **World shadow pool** (`ShadowWorldEffects.renderMegumiShadowPool` after PR 8): `setColor(0, 0, 0, alpha)` on `RenderType.debugTriangleFan()`. Already true black. Not the culprit.
2. **`MEGUMI_SHADOW_MOTE` particle** (`MegumiShadowMoteParticle`): 1-in-5 particles is an `accent` mote with `rCol=0.10, gCol=0.92, bCol=0.80` at `alpha=0.95` and `getLightColor() = 0xF000F0` (full-bright). Full-bright saturated teal at 95% alpha reads as the dominant color of the cloud even at 20% population. Summon fires a 14-mote burst plus a 10-mote ring; recall fires a 14-mote ring (`MegumiVfxRecipes`).
3. **`SHADOW_TEAL` / `SHADOW_DARK` dust** in `MegumiVfxRecipes`: `SHADOW_TEAL = 0x2F8F83` — used only in `sic` and `pounce` recipes, not summon/recall. Relevant only if the blue is also seen on Sic.

### Fix steps

1. Kill or desaturate the accent mote: set accent colors to near-black (e.g. keep a faint violet/desaturated edge only if a highlight is wanted), drop the full-bright `getLightColor` override for accents. This is the primary fix candidate and touches one file.
2. Re-check `SHADOW_DARK = 0x102E2B` — it is a dark teal; if the target look is neutral black shadow, shift it to neutral gray-black.
3. If Sic/pounce must also lose the teal, retune `SHADOW_TEAL` in the same pass; otherwise leave it (Sic is a marker effect, not the shadow pool).
4. Visual verification only: screenshots of summon, recall, sic, pounce at several times of day, before/after. There is no automated way to prove 'reads black'. Keep world-pool code untouched — it is already black and pinned by `ShadowWorldEffectsTest`.

---

## Issue #20 — bound `CurseLinkOptionsPayload` decoding

### What the code does today (fully traced)

- `CurseLinkOptionsPayload.read`: `int size = buffer.readVarInt(); new ArrayList<>(size);` then `size` iterations of `readUUID(), readUUID(), ResourceLocation.parse(buffer.readUtf())`. No cap anywhere (`readUtf()` defaults to 32767 chars per string).
- Client receiver: `JujutsuClientNetworking` does `setScreen(new CurseLinkSelectionScreen(payload.entries()))` — decoded size feeds UI construction directly.
- Sender side: `CurseLinkRegistry.linksForParticipant` returns an unbounded sorted list; `CurseLinkSelection.resolve` treats `size() == 1` as auto-ready and larger lists as needing selection. There is **no natural maximum** in current code — the cap must be chosen and documented as a defensive constant, as the issue itself demands ('read off current code rather than guessed').
- Wire format per entry: 2 UUIDs + one string. Existing valid payloads must decode identically (issue constraint).

### Fix steps

1. In `CurseLinkOptionsPayload`:
   - Add `MAX_ENTRIES` (defensive constant, e.g. 64 — far above anything a 1-2 player private game produces; document that no natural max exists in code and this is a chosen bound).
   - Reject `size < 0 || size > MAX_ENTRIES` **before** allocating the `ArrayList` (do not pre-size from the untrusted value at all).
   - Bound strings with `buffer.readUtf(MAX_TECHNIQUE_ID_LENGTH)` — a `ResourceLocation` string needs at most ~2×32767? No: namespaces/paths are effectively short; pick e.g. 256 and document it. (Vanilla `readUtf(int)` exists and throws on overflow.)
2. Unknown/malformed id policy: `ResourceLocation.parse` throws on malformed input — catch per entry, drop the entry, log once. Whole-payload refusal is wrong here because one bad entry would nuke a legitimate list; document the drop-entry decision in the payload javadoc and KNOWN_ISSUES E2 resolution note.
3. Client-side defense in depth: `CurseLinkSelectionScreen` receives at most `MAX_ENTRIES` entries by construction; assert/document it rather than re-bounding the UI.
4. Negative codec tests (new JUnit, `src/test/java/jujutsu/mod/network/`): over-cap count rejected without allocating per count; over-length string rejected; malformed id dropped with the rest of the payload intact; valid payload round-trips byte-identical with the buffer fully consumed. Red mutations: raise/remove each bound -> matching test fails.
5. Close E2 in `docs/KNOWN_ISSUES.md` with the chosen constants and policy. Do not widen scope to other payloads (issue's own boundary).

---

## Shared verification

- `qualityGate --no-daemon --max-workers=1 --no-watch-fs` green after each PR.
- #30/#31: in-game smoke is mandatory (the issues came from in-game smoke; `qualityGate` cannot reproduce them).
- #29: before/after screenshots.
- #20: codec tests are the full verification; no in-game step needed.
- Documentation: MOC/SESSION/KNOWN_ISSUES updated per fix; E2 marked resolved only after the codec tests land.

## Out of scope

- No VFX Core architectural changes (freeze boundary from PR 9 holds; none of these fixes needs a new channel/packet/callback).
- No new GameTest source set; existing JUnit + JavaExec infrastructure suffices.
- No audit of other payloads beyond #20.
