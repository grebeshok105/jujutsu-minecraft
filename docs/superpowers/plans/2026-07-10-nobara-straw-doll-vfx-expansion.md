# Nobara Straw Doll and VFX Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Nobara's nail-fire treatment, deepen her cinematic impact language, and add a server-authoritative canon-forward Straw Doll Resonance ritual with an original animated 3D asset.

**Architecture:** Ordinary nail hits feed a small server-only remnant progression policy. A typed target-bound remnant item plus a reusable straw-doll item gates an interruptible Shift+hammer ritual; final validation and consumption happen at the impact tick. Existing typed VFX cues fan out through `VfxDirector`; one internal blur channel and richer named camera profiles extend the core without exposing a shader API or creating a parallel manager.

**Tech Stack:** Java 21, Fabric API for Minecraft 1.21.8, Mojang mappings, GeckoLib 5.2.2, existing JavaExec assertion tests, Blockbench source/export workflow. No new dependency.

## Global Constraints

- Work only in `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice` on `codex/nobara-cinematic-slice`.
- Keep gameplay server-authoritative; client cues are visual only.
- Use `VfxCue -> VfxDirector -> NobaraVfxRecipes` for all transient effects.
- Do not add Satin, Veil, Photon, a JSON VFX DSL, a parallel effect manager, or ProjectJJK runtime doll assets.
- Create the doll model, texture, UVs, and animations from scratch under the `jujutsumod` namespace.
- Do not change Hairpin Enlarge targeting: `R` requires a marked living target under the crosshair.
- Do not use Computer Use or UI automation. The user performs manual gameplay QA.
- Commit each verified task separately with a conventional English message.
- Never copy a runtime JAR before a successful final build.

## File Structure

- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRemnantProgress.java`: pure two-hit progression policy keyed by caster and target.
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkResonanceRemnant.java`: typed remnant identity and validation data.
- `src/main/java/jujutsu/mod/registry/JujutsuDataComponents.java`: target-bound remnant component registration.
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRemnantItem.java`: bound remnant item presentation.
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollItem.java`: reusable GeckoLib doll item and animation triggers.
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java`: remnant drops, pending ritual lifecycle, final validation, consumption, and damage.
- `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualPolicy.java`: pure range/dimension/resource decision functions used by runtime tests.
- `src/client/java/jujutsu/mod/client/render/nobara/doll/**`: GeckoLib model and renderer for the original doll.
- `src/client/java/jujutsu/mod/client/vfx/VfxPostProcessChannel.java`: internal short blur pulse with bounded lifetime and safe fallback.
- `src/main/resources/assets/jujutsumod/{geo,animations,textures/item}/straw_doll*`: exported original asset.
- `src/main/resources/assets/jujutsumod/items/{straw_doll,resonance_remnant}.json`: item definitions.
- `src/test/java/jujutsu/mod/character/nobara/projectjjk/{ProjectJjkRemnantProgressTest,ProjectJjkRitualPolicyTest}.java`: pure gameplay regression tests.

---

### Task 1: Target Remnant Contract and Progression

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRemnantProgress.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkResonanceRemnant.java`
- Create: `src/main/java/jujutsu/mod/registry/JujutsuDataComponents.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRemnantItem.java`
- Create: `src/test/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRemnantProgressTest.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuItems.java`
- Modify: `src/main/java/jujutsu/mod/JujutsuMod.java`
- Modify: `build.gradle`

**Interfaces:**
- Produces: `boolean ProjectJjkRemnantProgress.recordHit(UUID casterId, UUID targetId)` returning `true` exactly on every second hit and resetting that pair.
- Produces: `void ProjectJjkRemnantProgress.clearCaster(UUID casterId)` and `void clear()` for lifecycle cleanup.
- Produces: `record ProjectJjkResonanceRemnant(UUID targetId, ResourceLocation dimension, String targetName)` with `Codec` and `StreamCodec`.
- Produces: `JujutsuDataComponents.RESONANCE_TARGET` and `JujutsuItems.RESONANCE_REMNANT`.

- [ ] **Step 1: Write the failing progression test**

Create an assertion main that verifies first hit false, second hit true, reset after emission, caster isolation, target isolation, `clearCaster`, and global `clear`.

```java
ProjectJjkRemnantProgress progress = new ProjectJjkRemnantProgress(2);
assert !progress.recordHit(casterA, targetA);
assert progress.recordHit(casterA, targetA);
assert !progress.recordHit(casterA, targetA);
assert !progress.recordHit(casterB, targetA);
assert !progress.recordHit(casterA, targetB);
progress.clearCaster(casterA);
assert !progress.recordHit(casterA, targetA);
progress.clear();
```

- [ ] **Step 2: Register and run the failing test**

Add `testNobaraRemnant` to `build.gradle`, wire it into `check`, and run:

`gradlew.bat testNobaraRemnant --no-daemon`

Expected: compilation fails because `ProjectJjkRemnantProgress` does not exist.

- [ ] **Step 3: Implement the minimal progression and typed component**

Use a `Map<HitKey, Integer>` inside `ProjectJjkRemnantProgress`; remove a key when its count reaches the configured threshold. Register a persistent/network-synchronized `DataComponentType<ProjectJjkResonanceRemnant>` using the record codec and stream codec. Register a stack-size-1 `resonance_remnant` item.

- [ ] **Step 4: Verify the contract**

Run:

`gradlew.bat testNobaraRemnant testProjectSanity --no-daemon`

Expected: both tasks pass and item/component registrations compile on Minecraft 1.21.8.

- [ ] **Step 5: Commit**

```text
feat(nobara): add resonance remnant contract
```

### Task 2: Server-Authoritative Straw Doll Ritual

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualPolicy.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollItem.java`
- Create: `src/test/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualPolicyTest.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraLoadout.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuItems.java`
- Modify: `src/main/resources/assets/jujutsumod/lang/en_us.json`
- Modify: `src/main/resources/assets/jujutsumod/lang/ru_ru.json`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: `RESONANCE_TARGET`, `RESONANCE_REMNANT`, existing nail/hammer items and server VFX helpers.
- Produces: `void ProjectJjkStrawDollRuntime.onOrdinaryNailHit(ServerLevel, ServerPlayer, LivingEntity, Vec3)`.
- Produces: `boolean ProjectJjkStrawDollRuntime.tryStart(ServerPlayer, ItemStack hammer, InteractionHand hand)`.
- Produces: `ProjectJjkRitualPolicy.Validation` with explicit `OK`, `NO_DOLL`, `NO_REMNANT`, `NO_NAIL`, `TARGET_INVALID`, `WRONG_DIMENSION`, `OUT_OF_RANGE`, and `ALREADY_CASTING` outcomes.

- [ ] **Step 1: Write failing policy tests**

Verify same-dimension/range boundaries, invalid target/resource outcomes, and that the consumption decision is true only for `OK` at final resolution.

```java
assert ProjectJjkRitualPolicy.validate(true, true, true, true, true, 63.9, false) == Validation.OK;
assert ProjectJjkRitualPolicy.validate(true, true, true, true, false, 1.0, false) == Validation.WRONG_DIMENSION;
assert ProjectJjkRitualPolicy.validate(true, true, true, true, true, 64.1, false) == Validation.OUT_OF_RANGE;
assert !ProjectJjkRitualPolicy.shouldConsume(Validation.NO_NAIL);
assert ProjectJjkRitualPolicy.shouldConsume(Validation.OK);
```

- [ ] **Step 2: Run RED**

Register `testNobaraRitual`, wire it into `check`, and run:

`gradlew.bat testNobaraRitual --no-daemon`

Expected: compilation fails because the policy is absent.

- [ ] **Step 3: Implement remnant drops and ritual lifecycle**

On the second ordinary nail hit, create one remnant stack, attach `ProjectJjkResonanceRemnant`, and spawn it at the wound. Shift+right-click with the hammer must require the doll in the off hand, choose one bound remnant, validate without consuming, and enqueue a 14-tick pending cast. At due tick revalidate caster tools, target, same dimension, 64-block range, nail, and remnant identity; only then consume one nail and that remnant, apply existing Resonance damage/weakness, discard owned embedded nails, clear glow, and emit ritual cues. Reject duplicate pending casts and clear them on disconnect/server stop.

- [ ] **Step 4: Remove mark-only Resonance and update loadout**

Delete the two-step `ProjectJjkResonanceLink` path from hammer use. Keep mark-based Hairpin Enlarge/Explosion unchanged. Give one reusable `STRAW_DOLL` from `ProjectJjkNobaraLoadout`, and localize all validation outcomes.

- [ ] **Step 5: Run GREEN and regression checks**

Run:

`gradlew.bat testNobaraRemnant testNobaraRitual testProjectJjkNobaraProfile testProjectSanity --no-daemon`

Expected: all pass; `R` and `B` sanity assertions remain intact.

- [ ] **Step 6: Commit**

```text
feat(nobara): implement straw doll resonance ritual
```

### Task 3: Original Straw Doll Model, Texture, and Animation

**Files:**
- Create: `src/main/resources/assets/jujutsumod/geo/straw_doll.geo.json`
- Create: `src/main/resources/assets/jujutsumod/animations/straw_doll.animation.json`
- Create: `src/main/resources/assets/jujutsumod/textures/item/straw_doll.png`
- Create: `src/main/resources/assets/jujutsumod/items/straw_doll.json`
- Create: `src/main/resources/assets/jujutsumod/models/item/straw_doll.json` only if GeckoLib's 1.21.8 item definition delegates through a vanilla model.
- Create: `src/client/java/jujutsu/mod/client/render/nobara/doll/ProjectJjkStrawDollModel.java`
- Create: `src/client/java/jujutsu/mod/client/render/nobara/doll/ProjectJjkStrawDollRenderer.java`
- Create: `src/main/resources/source-assets/blockbench/straw_doll.bbmodel`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollItem.java`
- Modify: `src/test/java/jujutsu/mod/ProjectSanityTest.java`

**Interfaces:**
- Consumes: registered `STRAW_DOLL` item and GeckoLib 5.2.2.
- Produces: item animations `idle`, `ritual_raise`, `impact`, and `release` and runtime resources under `jujutsumod`.

- [ ] **Step 1: Load the required Blockbench skills and create a checkpoint**

Invoke `blockbench-use`, `minecraft-blockbench-asset`, `blockbench-modeling`, `blockbench-texturing`, and `blockbench-animation` before any Blockbench MCP mutation. Confirm the active format supports GeckoLib animation export and save the source `.bbmodel` before export.

- [ ] **Step 2: Build the original model and texture**

Create a compact asymmetric humanoid from separate straw bundles: cylindrical head, torso strike plate, uneven horizontal arms, split tapered legs, charcoal-green bindings, and rough loose ends. Use original proportions and a 32x32 or 64x64 hand-painted olive/yellow texture; do not open or trace ProjectJJK doll resources while constructing it.

- [ ] **Step 3: Animate and export**

Add restrained looping `idle`, a 14-tick `ritual_raise`, a fast torso `impact`, and a decaying `release` shudder. Export geometry/animation/texture to the exact runtime paths and retain the `.bbmodel` source.

- [ ] **Step 4: Wire the GeckoLib item renderer and RED guards**

Register the item renderer using the locally verified GeckoLib 5 API. Add sanity assertions that all four animations, runtime resources, texture dimensions, item definition, and source `.bbmodel` exist, and that no runtime straw-doll path references `.reference/projectjjk` or `assets/projectjjk`.

- [ ] **Step 5: Compile and inspect the asset**

Run:

`gradlew.bat compileClientJava testProjectSanity --no-daemon`

Render stills from the Blockbench project for front, side, and three-quarter inspection; verify the model is nonblank, UVs stay inside the texture, no cubes overlap incoherently, and the nail impact area reads at item scale.

- [ ] **Step 6: Commit**

```text
feat(nobara): add original animated straw doll
```

### Task 4: Nail Aura Redesign and Cinematic VFX Core Upgrade

**Files:**
- Create: `src/client/java/jujutsu/mod/client/vfx/VfxPostProcessChannel.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/VfxDirector.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/VfxContext.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/VfxCameraChannel.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/VfxWorldChannel.java`
- Modify: `src/client/java/jujutsu/mod/client/vfx/nobara/NobaraVfxRecipes.java`
- Modify: `src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java`
- Modify: `src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkStrawDollRuntime.java`
- Modify: `src/test/java/jujutsu/mod/ProjectSanityTest.java`
- Modify: `src/test/java/jujutsu/mod/vfx/VfxTimelineTest.java`

**Interfaces:**
- Produces: camera methods `triggerLaunch`, `triggerHeavyImpact`, `triggerExplosion`, and `triggerRitual` with `initialAgeTicks` overloads.
- Produces: internal `VfxPostProcessChannel.triggerBlur(int durationMillis, float initialAgeTicks)` and `render(Minecraft)`; no public recipe-independent shader API.
- Produces: new IDs `REMNANT_DROP`, `RITUAL_BIND`, `DOLL_STRIKE`, and `RESONANCE_RELEASE`.

- [ ] **Step 1: Write failing timing/registration guards**

Extend timeline/sanity assertions to require the four cue registrations, all age-aware camera/blur calls, director ownership/cleanup of `VfxPostProcessChannel`, and absence of `SOUL_FIRE_FLAME` from Nobara recipes and the persistent nail renderer.

- [ ] **Step 2: Run RED**

Run:

`gradlew.bat testVfxTimeline testProjectSanity --no-daemon`

Expected: assertions fail on missing IDs/channel/registrations and the old flame composition.

- [ ] **Step 3: Replace nail fire with compressed-energy rendering**

In `ProjectJjkNailRenderer`, keep the metal item dominant. Draw a narrow rim and sparse bands for prepared nails, a tip core plus directional tail and orbiting slivers for launched nails, and no broad aura for embedded nails. Replace `HAIRPIN_IGNITION_TICK`/`SOUL_FIRE_FLAME` use that reads as fire with compression motes, sparks, metal shards, cyan dust, and geometry.

- [ ] **Step 4: Add bounded blur and named camera profiles**

Have the director own and clear one `VfxPostProcessChannel`. During the registered world render callback, process a blur pulse only while active, world/player are valid, and the call is on the render path. Catch runtime/resource failure once, disable later blur calls for that session, and leave HUD/camera/world effects active. Clamp accumulated yaw, pitch, and FOV after sampling all impulses.

- [ ] **Step 5: Compose distinct Nobara scenes**

Update hammer, impact, detonate, enlarge, and explosion recipes with separate camera/FOV timing and heavier rings/blades/fracture shells. Add remnant drop, ritual bind, doll strike, and remote release recipes. Gate local blur by proximity and use actual `initialAgeTicks` for every realtime channel.

- [ ] **Step 6: Run GREEN and client compilation**

Run:

`gradlew.bat testVfxCore testVfxTimeline testVfxQuality testVfxAnchor testProjectSanity compileClientJava --no-daemon`

Expected: all pass; no new dependency or per-effect callback/mixin exists.

- [ ] **Step 7: Commit**

```text
feat(vfx): deepen nobara cinematic effects
```

### Task 5: Documentation, Independent Review, Build, Install, and Handoff

**Files:**
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Nobara-overview.md`
- Create: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Straw-Doll-resonance.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/Hairpin-effects.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/Risks-and-tech-debt.md`
- Create: `docs/session-handoffs/2026-07-10-nobara-straw-doll-vfx-handoff.md`
- Modify: `SESSION.md`

**Interfaces:**
- Consumes: completed gameplay, asset, and VFX commits.
- Produces: source-backed current architecture notes, exact verification evidence, installed JAR hash, and manual-QA checklist.

- [ ] **Step 1: Update the versioned codex**

Document the remnant threshold as a Minecraft adaptation, the verified canon invariant, item/component/runtime paths, new cue recipes, original asset boundary, blur fallback, removed mark-only Resonance, and exact unresolved manual QA. Link the new note from the MOC and update cited line numbers after implementation stabilizes.

- [ ] **Step 2: Run independent reviews**

Dispatch separate read-only reviewers for gameplay/server authority, VFX/client lifecycle, asset originality/resource completeness, and spec coverage. Fix Critical/Important findings with focused tests and small commits; re-run the affected reviewer until no blocking findings remain.

- [ ] **Step 3: Run fresh final verification**

Run:

```text
gradlew.bat check --no-daemon
gradlew.bat build --no-daemon -x test
git diff --check
```

Expected: both Gradle commands report `BUILD SUCCESSFUL`; all custom assertion tasks pass; no whitespace errors.

- [ ] **Step 4: Install and hash-verify the runtime JAR**

Replace only the old non-sources/non-dev `jujutsumod` JAR in `D:\Games\instances\Jujutsu\mods` with `build\libs\jujutsumod-1.0.0.jar`. Compare source/destination SHA-256 and sizes; require equality.

- [ ] **Step 5: Write the full session handoff**

Record starting/final commits, how the original bug/list was investigated, canon sources, design decisions, exact files and commits, TDD RED/GREEN evidence, reviewer findings/fixes, build and JAR hashes, model source/runtime paths, and the manual test matrix. Mark gameplay feel, reduced-particle behavior, and two-client observation as unverified until the user tests them.

- [ ] **Step 6: Commit**

```text
docs(session): hand off straw doll vfx expansion
```

## Manual QA Matrix

- Prepared nails: readable metal, no torch/flame silhouette.
- Launched nails: directional compressed-energy tail; embedded nails remain restrained.
- `R`: marked target under crosshair produces delayed Enlarge; missing target remains an invalid cast, not a regression.
- `B`: embedded nails detonate with staggered shells and strong but bounded camera response.
- Remnant: exactly one bound item appears on every second ordinary hit for the same caster/target.
- Ritual: doll off hand + hammer main hand + nail + valid remnant starts the telegraph and consumes resources only at successful impact.
- Invalid ritual: dead/unloaded/wrong-dimension/out-of-range target consumes nothing.
- Remote Resonance: target behind walls inside the supported range is hit.
- Visuals: doll bind/strike/release, target fracture bloom, short blur, FOV compression/rebound, and no stuck camera/post-process state.
- Settings: ALL, DECREASED, and MINIMAL particle options remain readable.
- Multiplayer: caster and observer see the same server-confirmed ritual stages; neither client can apply gameplay locally.
