# Review Block 2 — server core (entity, tiers, variants, AI, seams, tags)

Reviewer: ReviewBlock2. Read-only audit of the Block 2 ownership row on branch
`feat/cursed-spirits` (HEAD `11e8541` at review time).
Scope files: `cursedspirit/**` (main), `combat/{StaggerResistant,CombatTags}.java`,
seam edits (`CharacterCombatModifiers`, `TodoBoogieWoogieRuntime`), `JujutsuEntities`,
`JujutsuSounds` (cursed rows), `JujutsuMod` hook, tag JSONs, `cursedspirit/**` unit tests
(except `CursedSpiritSoundSymmetryTest`, `CursedSpiritResourceContractTest`),
`gametest/{CursedSpiritGameTests,CursedSpiritTestFixtures}.java`.
In scope: R1, R3, R4, R5, R11–R17(tag), R20, R24, R25.

## Method (what I actually ran / read)

- `git diff main...HEAD --stat` (108 files), `git log main..HEAD` (14 commits on the branch).
- Read in full: `CursedSpiritEntity`, `CursedSpiritAttackGoal`, `CursedSpiritAttackPolicy`,
  `CursedSpiritProfile`, `CursedSpiritVariant`, `CursedSpirits`, `CombatTags`,
  `StaggerResistant`, `CharacterCombatModifiers.adjustedStaggerTicks`,
  `TodoBoogieWoogieRuntime.isEligibleTarget`, both tag JSONs (via `cat`),
  `CursedSpiritGameTests` (744 lines), `CursedSpiritTestFixtures`,
  `CursedSpirit{Profile,Variant,AttackPolicy,TagContract,Registry,TargetResolver}Test`,
  `JujutsuEntities.createCursedSpirit`, `JujutsuSounds` cursed rows, `CursedSpiritRenderer`,
  `CursedSpiritRenderState`, prowler/floater/gulber/guzzler/kelvin `setupAnim`s.
- Sanctioned suite:
  `gradlew.bat test --tests "jujutsu.mod.cursedspirit.*" --tests "jujutsu.mod.combat.*"`
  `--no-daemon --max-workers=1 --no-watch-fs` (JAVA_HOME Temurin 21) →
  **46 tests, 0 failures, 0 errors** (per-class XML counts: AttackPolicy 7, Profile 5,
  Registry 4, ResourceContract 5, SoundSymmetry 3, SpawnRules 5, TagContract 2,
  TargetResolver 2, Variant 7, CombatStaggerClear 2, SafeBodyPlacement 4).
- Standalone isolation probe:
  `test --tests "jujutsu.mod.cursedspirit.CursedSpiritVariantTest"` → **3/7 FAILED**
  deterministically (see F2). All other standalone probes passed.
- Lane (`runGameTest`/MCP) NOT run per instructions; GameTest verdicts below are
  oracle-quality assessments + explicit manual checks.

## Verified agreements (spot-checked against the tree, not the report)

- **Ctor order (twice-bitten class): SAFE.** `defineSynchedData` defines `DATA_VARIANT=0`
  with no roster/tier touch (`CursedSpiritEntity.java:189-196`); `variant()` is total for
  `tier==null` and cross-tier ordinals (`:80-93`); `CursedSpiritAttackGoal` ctor only stores
  the mob ref, stats read lazily via `stats()` (`CursedSpiritAttackGoal.java:30-33`);
  `registerGoals` registers all 5 goals + 2 target goals (`Entity:262-270`) and runs from the
  `Mob` super-ctor on every construction path (goals hold the live mob ref, tier set just
  after — later ticks read the real tier). `adjustIncomingStaggerTicks` is never called
  during construction.
- **Animation contract agrees both sides.** Ids 61/62/63/64 + `SCREAM_DURATION_TICKS=12`
  (`:46-51`); `handleEntityEvent` stop+start/stop semantics (`:228-238`,
  `beginAttackAnim/beginScreamAnim` stop-then-start, `end*` stop); `tick()` starts idle when
  stopped and the server decrements `screamTicks` → broadcasts 64 at zero (`:241-257`).
  Client consumer agrees: `CursedSpiritRenderer.extractRenderState` copies idle/attack/scream
  + variant; prowler/kelvin `setupAnim` gate walk on `!state.scream.isStarted()`; gulber/
  guzzler (no scream channel) correctly have NO scream gate and no dead clip; floater has no
  walk clip and wires attack explicitly. `hurtServer` screams only when
  `variant().screamSound() != null` and only on accepted damage (`:208-219`).
- **Persistence contract holds.** NBT key `Variant`, string id write (`:199-202`); load via
  `resolveLoadedVariant` — valid id kept, unknown/cross-tier re-rolled (`:135-142`); pinned by
  `CursedSpiritVariantTest.resolveLoadedVariantKeepsValidAndRerollsUnknownOrCrossTier` (green
  in suite).
- **Seams minimal and correctly placed.** `adjustedStaggerTicks`: `<=0` passthrough preserved,
  player path first and unchanged, `StaggerResistant` branch added, default passthrough
  (`CharacterCombatModifiers.java:47-57`). `isEligibleTarget` gains only the
  `!isBoogieWoogieImmune` conjunct (`TodoBoogieWoogieRuntime.java:124-135`); the same gate
  automatically covers pair-swap and stone-swap via the shared method. Tag contents verified
  by `cat`: immune = greater only; curse-remnant = all three. No second stagger/knockback
  implementation in the new code (`grep` in `cursedspirit/`: stagger only via
  `adjustIncomingStaggerTicks` + `CombatStagger.GLOBAL` read in the goal; knockback only via
  profile rows → vanilla `knockback()` + `KNOCKBACK_RESISTANCE` attribute).
- **R12 seam is genuinely exercised in-world.** `CombatStagger.java:18`: `GLOBAL.apply`
  routes through `CharacterCombatModifiers.adjustedStaggerTicks(entity, ticks)`, so the R12
  GameTest's `GLOBAL.apply(spirit, …, 20)` covers the `StaggerResistant` override, not just
  the stagger store. (COMMON multiplier 1.0 → present at tick 2, cleared by tick 26 ✓.)
- **R20 centralization verified by reading, not just the linkage test.** Every gameplay
  number in `CursedSpiritEntity`/`CursedSpiritAttackGoal` comes from `CursedSpiritProfile.of`
  / the passed row (attributes, stagger scale, damage, AoE, knockback, cooldowns, reach);
  `grep` shows no balance literals in either file (only epsilons `1.0E-6`, look-control
  `30.0f`, stroll `0.8`, look range `8` — AI tuning outside the frozen profile, acceptable).
- **Registration complete.** `JujutsuEntities.createCursedSpirit`: MONSTER, frozen hitboxes
  (0.85×1.0 / 0.75×1.9 / 1.35×2.4), `clientTrackingRange(96).updateInterval(3)`, no `.noSave()`;
  `CursedSpirits.registerServerHooks` = 3 attribute rows + Block 4's spawn line; one-line
  `JujutsuMod` hook present; 34 `cursed.*` sounds registered; `fabric.mod.json` lists
  `CursedSpiritGameTests` (+ Block 4/5 classes, untouched).
- **GameTest oracle quality (read, all 12 scenarios).** Premises asserted before behaviour
  everywhere (hostile difficulty, victim validity incl. `isInvulnerableTo`, LOS,
  `getTarget()` poll before HP). No `spawnWithNoFreeWill` for mobile bodies; `freezeGround`
  = AI + Slowness-100 (physics intact). Damage bands are production-derived (45.0 profile HP
  in R11; 0.10 floor under the sampled ~0.19 post-friction burst in R4 with a `strikeStep→0`
  red-proof claim; 3.0/5.0 geometry vs the 3.5 AoE radius in R5; 3.0/4.24 vs ~3.175 reach
  boundary in R24). No tautologies found; failure paths discard bodies (no cross-arena
  leakage); R14/R15 go through the production swap executor with position-epsilon +
  cooldown asserts. Two soft spots, both covered by unit tests: R13's ordering pins the
  vanilla resistance attribute more than custom code (acceptable — the attribute row IS the
  implementation), and R24's far-victim oracle is implied by acquisition + the unit-pinned
  `inReach` boundary (`inReachBoundaryUsesBothHalfWidths`).

## Findings

### F1 (P2) — `block-2-report.md` is committed truncated; red-proofs unverifiable
File: `.superpowers/rule-of-four/cursed-spirits/block-2-report.md:58-62` — the file ends
mid-section with a literal `...[truncated 7375 chars]`. The plan requires per-scenario
red-proofs and `Факт:` lines in this report; as committed, the R4 re-pin ground truth, the
12 per-scenario red-proofs (mutation → failing assert), and the lane counts are missing, so
Main cannot verify R27 for this block from the artifact.
Counter-proposal: re-emit the full report (red-proof table: scenario → production mutation →
observed failure message → restore commit) in a follow-up commit. Confidence 1.0 (observed
via `tail -c`).

### F2 (P3) — `CursedSpiritVariantTest` fails standalone (missing Bootstrap)
File: `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritVariantTest.java:83-97` (first two
failing call sites; third at `:113`). The three tests touching `CursedSpiritEntity`
(`selectVariantIndex`/`rollVariant`/`resolveLoadedVariant`) trigger the entity class init
(`SynchedEntityData.defineId`), which throws `ExceptionInInitializerError` without a prior
`Bootstrap.bootStrap()`. Green in the sanctioned suite only because `CursedSpiritRegistryTest`
bootstraps first in the same fork — order-dependent.
Counter-proposal: add the same `@BeforeAll Bootstrap.bootStrap()` the sibling
`CursedSpiritRegistryTest` uses (or move the pure `selectVariantIndex` core to a Bootstrap-free
home). Confidence 0.95 (deterministic standalone repro, two consecutive runs).

### F3 (P3) — `strike()` has no strike-time reach/LOS re-check (phantom-hit window)
File: `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritAttackGoal.java:118-135`. STRIKE is
entered from WINDUP on the windup clock alone (`advance` ignores `inReach` for WINDUP→STRIKE),
and `canContinueToUse` deliberately drops the LOS requirement, so a victim that sprints out of
reach (5–14 tick windup ≈ 1.5–4 blocks at sprint speed) or breaks LOS mid-swing still takes
full direct damage. The `canContinue` looseness is documented as deliberate; the missing
strike-time guard is not discussed.
Counter-proposal (design decision for Main): either accept as melee forgiveness and record it
in `KNOWN_ISSUES.md`, or add `if (!inReach(...)) { enter(target, APPROACH); return; }` at the
top of `strike()`. Confidence 0.6 on intent (code fact certain, whether it is a bug is a tuning
call). Not a verdict blocker.

## Per-requirement verdicts

| Req | Verdict | Evidence actually observed |
|---|---|---|
| R1 | MANUAL_VERIFY_REQUIRED (unit PASS) | `CursedSpiritRegistryTest` 4/4 green in my run (ids, MONSTER, frozen hitboxes, `canSerialize`, field identity); factory read matches the frozen table. In-world spawn presence needs Main's lane. |
| R3 | MANUAL_VERIFY_REQUIRED | Oracle sound: LOS + full-HP + difficulty premises, `getTarget()==victim` checked at damage tick, first-strike detection, cleanup on all paths. Lane run pending. |
| R4 | MANUAL_VERIFY_REQUIRED | Oracle (`peak ≥ 0.10` + damage premise) matches the code path (`strikeStep` impulse + `hurtMarked`); 0.10 floor is below the stated ~0.19 sample with margin. Red-proof text claimed but in truncated region (F1) — re-verify from lane. |
| R5 | MANUAL_VERIFY_REQUIRED | Geometry checks out (3.0 ≤ 3.5 splash, 5.0 excluded; spherical filter after box query); frozen body holds geometry. Lane run pending. |
| R11 | MANUAL_VERIFY_REQUIRED | Exact-HP oracle (45−5−7=33) with invuln-window spacing and `!isInvulnerable` premise; both sources asserted accepted. Lane run pending. |
| R12 | MANUAL_VERIFY_REQUIRED | Seam routing code-verified (`CombatStagger:18` → `adjustedStaggerTicks` → override with floor 1); GameTest present/cleared windows consistent with 20 ticks. Lane run pending. |
| R13 | MANUAL_VERIFY_REQUIRED | Oracle pins the frozen rows (0.0/0.0/0.85) via vanilla `knockback()` on AI+Slowness bodies; equal-pair closeness + greater damping asserts are satisfiable only by the profile rows. Lane run pending. |
| R14 | MANUAL_VERIFY_REQUIRED | Production-executor swap for LESSER and COMMON with same-tick capture, epsilon position exchange, LOS/range premises, cooldown assert. Lane run pending. |
| R15 | MANUAL_VERIFY_REQUIRED | Atomic-deny oracle (`routed:false`, field-for-field `assertBodyState` both bodies, zero cooldown). Lane run pending. |
| R16 | PASS (unit half) | `CursedSpiritTargetResolverTest` 2/2 green (centred-far beats near-graze; nearer pierced wins) through the existing `resolveForTests` seam, no new resolver code. Live half belongs to Block 5. |
| R17(tag) | PASS (tag half) | `cat` of both JSONs + `CursedSpiritTagContractTest` 2/2 green + in-world `type.is(...)` asserts for all three tiers. Progression half belongs to Block 5. |
| R20 | PASS | One profile class; `mutatedProfileRowMovesDerivedDamage` green; code read confirms goal/entity/attributes read rows, no duplicated literals. |
| R24 | MANUAL_VERIFY_REQUIRED | Unit boundary pinned (`3.325/3.326`); in-world near/far geometry consistent with `inReach` (3.0 in, ~4.24 out); acquisition poll removes the single-tick race. Lane run pending. |
| R25 | MANUAL_VERIFY_REQUIRED | Lethal `hurtServer(genericKill, MAX)` → `!isAlive` at tick 2, `isRemoved` at 26, absent at 36 — consistent with deathTime-20 removal. Lane run pending. |

## Manual checks for Main (what to do, what good looks like)

1. Lane: `gradlew.bat runGameTest --no-daemon --max-workers=1 --no-watch-fs` → all 12
   `CursedSpiritGameTests` scenarios green in `build/test-results/gametest/junit.xml`; any red
   names the broken assumption via its `diagnostic(...)` message.
2. Red-proof sample (F1 follow-up): set COMMON `strikeStep` → 0.0 → `commonStrikeCarriesStepBurst`
   must FAIL on the `≥ 0.10` assert with peak `0.0`; restore.
3. Red-proof sample: invert the `aoeRadius() > 0.0` gate → `greaterSlamSplashesBodiesInsideRadius`
   must FAIL on the inside-pig assert; restore.
4. F2 fix verification: `test --tests "...CursedSpiritVariantTest"` standalone green after the
   Bootstrap `@BeforeAll`.

## Scope verdict: MANUAL_VERIFY_REQUIRED

No P0/P1 defects; ctor-order, animation sync, persistence, seams, tags, registration, and all
46 unit tests verified green in the sanctioned command. Every in-world requirement is
structurally sound at the oracle level but awaits Main's lane evidence by design; F1 (truncated
worker report) must be repaired before R27 can be closed for this block.
