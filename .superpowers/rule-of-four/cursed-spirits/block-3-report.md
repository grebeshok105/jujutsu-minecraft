# Block 3 report — client render stack

## What was done
- Phase A: ported all 9 animation classes (package rename only, keyframes
  byte-identical) into `src/client/.../cursedspirit/anim/`, keeping only the frozen
  shipped clips, applying the KELVIN SCREAMER retarget, restoring the decompiler's
  private utility ctors where authored (gulber/guzzler/wistiver).
- Phase B: `CursedSpiritRenderState`, 9 model ports (`EntityModel`, `super(root)`,
  verbatim `createBodyLayer`, `setupAnim` per port rules), `CursedSpiritModels` rig
  table, `CursedSpiritRenderer` (TropicalFish dispatch), `CursedSpiritClient`
  (9 layers + 3 tier renderers), one line in `JujutsuModClient`, two contract tests.
- Commits: `4b9d29d feat(cursed-spirits): first rig (prowler)`,
  `dc4ab63 feat(cursed-spirits): vanilla model/animation port and tier renderers`.

## Files created
- `src/client/java/jujutsu/mod/client/render/cursedspirit/anim/CursedSpirit{Blud,Butcher,FloatingCurse,Gulber,Guzzler,Kelvin,Prowler,WalkingBed,Wistiver}Animations.java`
- `src/client/java/jujutsu/mod/client/render/cursedspirit/model/CursedSpirit{Blud,Butcher,FloatingCurse,Gulber,Guzzler,Kelvin,Prowler,WalkingBed,Wistiver}Model.java`
- `src/client/java/jujutsu/mod/client/render/cursedspirit/{CursedSpiritRenderState,CursedSpiritModels,CursedSpiritRenderer,CursedSpiritClient}.java`
- `src/test/java/jujutsu/mod/client/render/cursedspirit/{CursedSpiritRigFixtures,CursedSpiritRigContractTest,CursedSpiritAnimationContractTest}.java`

## Files modified
- `src/client/java/jujutsu/mod/client/JujutsuModClient.java` (+2/−0: import +
  `CursedSpiritClient.register();`)

## Acceptance
- Факт: `gradlew.bat compileClientJava --no-daemon --max-workers=1 --no-watch-fs` → `BUILD SUCCESSFUL` (2026-09-12, final run together with tests).
- Факт: `gradlew.bat test --tests "jujutsu.mod.client.render.cursedspirit.*"` → `BUILD SUCCESSFUL`; `TEST-...CursedSpiritRigContractTest.xml`: `tests="5" failures="0" errors="0"`; `TEST-...CursedSpiritAnimationContractTest.xml`: `tests="3" failures="0" errors="0"` (2026-09-12).
- Факт: `git grep -i sonsofsins -- src/` → zero hits (2026-09-12).
- Факт: `grep -ri gecko src/client/java/jujutsu/mod/client/render/cursedspirit/` → zero hits (2026-09-12).
- Факт: `git diff -- src/client/.../JujutsuModClient.java` → `2 insertions, 0 deletions` (2026-09-12).
- Факт: no server/asset/`fabric.mod.json` file touched by this block — final `git status` shows only `cursedspirit/**`, `JujutsuModClient.java` (committed) and the two test files (committed); foreign worktree edits (gametest, `CursedSpiritAttackGoal/Entity/Spirits`, `fabric.mod.json`) belong to other blocks and were not staged.

## Red-proofs (each: mutate → observe failure → restore → final green re-verified)
1. Rename one clip bone (`leftarm` → `leftarm_renamed` in KELVIN SCREAMER) → `clipBoneSetsAndCountsMatchFrozen` FAILED (`bone set for KELVIN/SCREAMER ==> expected: <[body, rightarm, leftarm, flater, left_leg, right_leg, bone4, head]> but was: <[..., leftarm_renamed, ...]>`) and `everyClipBoneExistsOnBakedModel` FAILED (`clip bone leftarm_renamed of KELVIN/SCREAMER missing from baked model`) → restored.
2. Drop one channel (sole `head` channel of KELVIN IDLE) → `clipBoneSetsAndCountsMatchFrozen` FAILED (`bone set for KELVIN/IDLE ==> expected: <[body, rightarm, head, leftarm]> but was: <[rightarm, leftarm, body]>`) → restored.
3. Swap two clip keys (KELVIN IDLE ↔ WALK) → `clipBoneSetsAndCountsMatchFrozen` FAILED (`bone set for KELVIN/IDLE ==> expected: <[head, rightarm, body, leftarm]> but was: <[head, left_leg, right_leg, rightarm, leftarm, body]>`) → restored.
4. Flip one looping flag (removed `.looping()` from KELVIN IDLE) → `loopingFlagsMatchFrozen` FAILED (`looping for KELVIN/IDLE ==> expected: <true> but was: <false>`) → restored.
5. Delete one texture (`cursed_kelvin.png`) → `everyTextureExists` FAILED (`missing texture src\main\resources\assets\jujutsumod\textures\entity\cursed\cursed_kelvin.png ==> expected: <true> but was: <false>`) → restored via `git checkout`.

## Port decisions / deviations
- FLOATING_CURSE source is `Modelcurse_ghost.java`, not `Modelcurse.java` (port-map line 11 is wrong; `analysis/rig_audit.py:14` already pairs the ghost; ghost parts `all/flater/body/head/bone/spine` are exactly the frozen 6). Its `setupAnim` ships SCREAMER+IDLE with no attack line — the plan's note; the port keeps pack order and appends the mandated `state.attack → ATTACK`. No WALK clip → glides.
- Head formula read via `javap -c` on 1.21.8 `HumanoidModel.setupAnim`: `head.yRot = state.yRot * 0.017453292f`, `head.xRot = state.xRot * 0.017453292f` (identical math to the pack's `/ 57.295776f`).
- GULBER walk unconditional (pack gate was a sit flag our entity never sets; WALK args (2.5, 4.0)); GUZZLER walk unconditional (2.0, 4.0), no head lines as authored.
- WALKING_BED grave branches unreachable without grave state → IDLE branch, scream-gated WALK (2.5, 4.5); MASK/CRAWL dropped (not shipped).
- WISTIVER GRAVE exists upstream but is not ported (no driver state) (render state carries exactly the frozen idle/attack/scream); pinned by tests, application awaits a later slice.
- 1.21.8 findings: `TexturedModelData` is gone — `EntityModelLayerRegistry` takes a `LayerDefinition` supplier; bone-name checks use public `ModelPart.createPartLookup()`; `AnimationDefinition.bake(ModelPart)` is public; `AnimationState.copyFrom` exists; `EntityModel(ModelPart)` protected ctor exists, `root`/`root()` final in `Model`.

## Risks / unresolved
- WISTIVER GRAVE and FLOATING_CURSE IDLE/SCREAMER clips are pinned but only ATTACK (curse) is wired beyond pack order; grave application needs a future event/state.
- `JujutsuModClient` was briefly damaged mid-work by range-replace edits (two lines overwritten); caught by advisory, restored, and verified `+2/−0` via `git diff`. Final file state confirmed green by `compileClientJava`.
- Two transient cross-block frictions (both resolved, no action): ServerCore's mid-flight `CursedSpiritEntity` breakage delayed the test run; two stale "still red" notices from ServerCore referred to already-fixed states.
