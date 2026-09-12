# Plan review — Phase 1.5 (rule-of-four)

Three read-only reviewers ran against `implementation-plan.md` rev 1 (PlanBuildability-2 / PlanVerifiability-2 / PlanCompleteness-2).
Every finding below is adjudicated: **ACCEPTED** (folded into rev 2/3), **REJECTED** (with evidence), or **NOTED** (recorded only).
Rev 2 folded the completeness/verifiability findings; rev 3 folded the buildability findings. Dispatch of Blocks 1/2 happened only after their owning findings were closed.

## Verdicts as delivered

- **PlanVerifiability-2:** "plan does not yet gate proof" — 4 requirements (R4, R5, R7, R9, R16) had no concrete verifier, hostile targeting mechanism unnamed, Block 3/4 oracles pass while broken, MCP lane relied on non-existent oracles. → rev 2 closed each item.
- **PlanCompleteness-2:** one P0 dependency cycle (Block 2 → Block 4 file), six "truncated" contract lines, missing per-variant sound-channel table, nominal-only R mappings; deviation verdict on the per-tier live checks. → rev 2 closed; the truncation finding was rejected with evidence.
- **PlanBuildability-2:** one P0 (`SpawnPlacements.register` private in 1.21.8 → Block 4 didn't compile), P1 animation-event lifecycle incomplete, 4×P2 + 4×P3, plus a large explicitly verified-OK list. → rev 3 closed all.

## P0 findings and their closure

| # | Finding | Adjudication | Closure in plan |
|---|---|---|---|
| 1 | Block 2 final tree called Block 4's `CursedSpiritSpawnIntegration.register()` → forward dependency cycle, `Depends: none` false | ACCEPTED | Block 2 ships without the call; Block 4 owns the one-line edit to `CursedSpirits.java` after the named gate `block-2 report filed` (Task 2 dispatch gate + ownership matrix + serialization gates) |
| 2 | `SpawnPlacements.register` is private in 1.21.8 (javap on the real jar); no Fabric wrapper; repo has no access widener → Block 4 does not compile | ACCEPTED (verified independently by Main via javap: `private static <T extends Mob> void register(...)`) | D3 rewritten: gate lives in the public `Mob.checkSpawnRules(LevelAccessor, EntitySpawnReason)` override (Main additionally verified with `javap -c` that `NaturalSpawner` calls it, and that unregistered types keep `NO_RESTRICTIONS` + `MOTION_BLOCKING_NO_LEAVES` defaults, so vanilla position choice still works). No access widener needed — public API only |
| 3 | `SpawnReason` no longer exists in 1.21.8 (it is `EntitySpawnReason`) | ACCEPTED | Block 4 steps use `EntitySpawnReason` |
| 4 | Block 4 step-4 smoke asserts were vacuous (`getPlacementType`/`getHeightmapType` return defaults for unregistered types) | ACCEPTED | Replaced by a real in-world GameTest: lit cell → false, dark cell → true, over-cap → false |
| 5 | Hostile targeting never named a target-acquisition goal → R3/R13 unprovable | PARTIALLY ACCEPTED (the goal WAS in rev 1 line 157, but that line exceeded the reviewers' 768-char read limit and was clipped; the deeper point — no `getTarget()` contract, no premises — was real) | Step 6 names the targetSelector goals and the `getTarget()` contract; Step 10 adds premise asserts + `getTarget()==victim` oracle; all long plan lines are now wrapped ≤ 700 chars |
| 6 | Animation event lifecycle incomplete: byte ids unfrozen, no scream trigger, no stop (`startIfStopped` is sticky → 2nd attack invisible; looping SCREAMER holds the walk gate forever) | ACCEPTED (verified against the ported `setupAnim`s) | Frozen ids 61/62/63/64, stop+start semantics, server-side scream timer, walk gate on `!state.scream.isStarted()`, no death event |
| 7 | T2/T3 mechanics (COMMON lunge, GREATER AoE) had no in-world oracles | ACCEPTED | Step 10: strike-step velocity burst assert; AoE damages inside radius and not outside |
| 8 | R7/R9/R16 missing verifiers; R6 used a bypass proxy (`hurtServer(hairpin)`) | ACCEPTED | Block 5 step 1 routes through `NobaraAbilityRouter.tryCast` aimed/mis-aim; `TodoAbilityRouter.tryCast` + Black Flash delta; Block 2 step 9 TargetResolver comparator JUnit; live ranking smoke in Block 5 |
| 9 | Block 3 rig test passed while broken (subset check: dropped channels vacuous, swapped keys iterate the wrong set) | ACCEPTED | Exact clip-key sets + exact bone counts (both directions) + looping flags from the frozen clip map; five red-proofs (rename / drop channel / swap keys / flip looping / delete texture) |
| 10 | Block 4 spawn rules self-contradictory (level param vs level-free JUnit) and non-deterministic against vanilla's rolled darkness gate | ACCEPTED | Pure `belowCap(int,int)` core + level adapter; vanilla darkness delegated to `super` (no re-implementation); boundaries tested deterministically |

## REJECTED findings

- **"Six plan lines are truncated mid-sentence" (completeness, P1).** REJECTED with evidence: the lines are complete (measured lengths 850–1244 chars); the reviewer's reader clips at 768 chars. Root cause was line length, not missing content. Action taken anyway (the underlying usability problem is real): `analysis/wrap_plan_lines.py` wrapped every >700-char line; a follow-up check reports zero such lines.
- **"`analysis/` is empty and `scout-3-report.md` does not exist" (buildability, P2).** NOTED as stale-read: the report file existed before the finding was filed (written from the scout payload) and `analysis/rig_audit.py` predates the review; both are present. The pointer in Block 1 step 1 was nevertheless corrected (the mono/44.1 kHz claim now sources `scout-3-report.md` addenda instead of a non-existent committed script).
- **"Block 2 goal set names only the attack goal" (verifiability, inside P0 #5).** PARTIALLY REJECTED — see #5; the goal list existed, the missing piece was the `getTarget()` contract and premises.

## NOTED (no plan change; recorded here and in progress.md)

- R5 wording in scout-4 demanded an "area/phase mechanic"; the plan delivers an AoE slam and **no enrage/phase state**. The AoE half is now oracle-covered; the phase half is deliberately out of scope for v1 (would be new design, not in the owner's brief).
- Manual-only verification boundary (cannot be automated headless): sound firing/coherence, animation timing feel, variant distinctness eyeball, world spawn feel, hitbox visual fit, T3 menace. Each has a stated manual method in Block 5 step 3 (Codex verification boundary) + the live pass.
- Reviewer-verified-OK list (no action): stagger seam shape, both fabric.mod.json entrypoints, `JujutsuSounds`/`sounds.json`/lang shapes, all 34 sound files present, GameTest fixture APIs (`setupTodoCaster`/`aimAt`/`castPrimary`, `spawnWithNoFreeWill` = spawn + removals), Model/LayerDefinition/animation API signatures, both renderer registries, `LivingEntityRenderer` plumbing incl. `scale(S, PoseStack)`/`getShadowRadius(S)`, gradle command validity, `REMNANT_HIT_THRESHOLD = 2`.
- Residual unverified-by-javap items (proven at compile or live): `HumanoidModel.setupAnim` yaw/pitch formula exactness (Block 3 must `javap -c` before writing), no loom access-widener pickup needed since no widener is used, exact MOC numbers (computed at build time), runtime `tools/list` output (Block 5 step 5 confirms before use).

## Deviation record

D8 in the plan: the owner's brief sketched per-tier sequential builds with live checks between tiers; the plan builds the shared foundation once (as the brief's own first requirement mandates) and performs per-tier live verification as a structured sequence (T1 → T2 → T3 → combined) in the final pass, plus a single-variant fail-fast probe at the end of Block 3 (`first rig landed`). Recorded in the plan and in `progress.md`.

## Phase 1.5 exit criteria (all met)

1. All three reviews delivered and adjudicated; every P0 closed in the plan text (rev 2/3).
2. No open question blocks Block 1/2 dispatch (Block 1 shipped; Block 2 dispatched after rev 3).
3. Blocks 3/4 stay gated on Block 2's `API landed`; their steps already reflect rev 3 contracts.
