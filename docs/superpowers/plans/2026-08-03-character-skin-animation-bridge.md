# Character Skin Animation Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render selected vessels with their ordinary Minecraft skin textures while GeckoLib continues to drive their existing third-person animation clips.

**Architecture:** Vanilla `PlayerRenderer` remains responsible for player geometry and layers. A shared client adapter evaluates each vessel's GeckoLib animation on an invisible humanoid rig, maps supported bone transforms to the live `PlayerModel`, and restores the model after rendering. Vessel definitions provide the adapter; shared dispatch has no character-name switch.

**Tech Stack:** Fabric 1.21.8, Minecraft 1.21.8 official mappings, Java 21, GeckoLib 5.2.2, JUnit 5/structural `ProjectSanityTest`, Gradle `qualityGate`.

## Global Constraints

- Keep server-authoritative gameplay unchanged.
- Keep GeckoLib 5.2.2 and all current animation/VFX trigger ids.
- Use public Fabric/Minecraft APIs and narrow Mixins only where vanilla rendering has no event hook.
- Keep client-only code under `src/client`.
- Do not add a generic server character abstraction or change payloads.
- Archive old visible player Geo files outside `src/main` and `src/client`; do not delete them.
- Final claim of verified completion requires a green `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs`.

---

### Task 1: Add failing skin-animation contract tests

**Files:**
- Modify: `src/test/java/jujutsu/mod/ProjectSanityTest.java`
- Test resources: `src/main/resources/assets/jujutsumod/geckolib/models/character_skin/`

**Interfaces:**
- Consumes: current `JujutsuCharacterClients`, `CharacterClientDefinition`, skin files, and the existing player render mixin config.
- Produces: executable assertions for the new adapter contract and archive boundary.

- [ ] **Step 1: Add RED assertions** for a definition-provided skin animation adapter, the three live skin textures, a live invisible rig for each animated vessel, removal of `CharacterRenderDispatchMixin` from the client mixin list, and presence of the archive manifest.

- [ ] **Step 2: Run the focused test** with `./gradlew.bat testProjectSanity --no-daemon --max-workers=1 --no-watch-fs`.

  Expected: FAIL because the adapter API, rig resources, archive manifest, and mixin change do not exist yet.

- [ ] **Step 3: Commit the RED test** with `test(render): define skin animation bridge contract`.

### Task 2: Add the shared GeckoLib rig-to-vanilla adapter

**Files:**
- Create: `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimation.java`
- Create: `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationAdapter.java`
- Create: `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationState.java`
- Create: `src/client/java/jujutsu/mod/client/render/CharacterSkinAnimationRenderer.java`
- Create: `src/main/resources/assets/jujutsumod/geckolib/models/character_skin/nobara.geo.json`
- Create: `src/main/resources/assets/jujutsumod/geckolib/models/character_skin/todo.geo.json`
- Create: `src/main/resources/assets/jujutsumod/geckolib/models/character_skin/megumi.geo.json`

**Interfaces:**
- Consumes: `GeoAnimatable`, `GeoModel`, `GeoRenderer.fillRenderState`, current vessel `PlayerGeoAnimatable` instances, `PlayerRenderState`, and the live `PlayerModel`.
- Produces: `CharacterSkinAnimationAdapter.apply(AbstractClientPlayer, PlayerRenderState, PlayerModel, float, int)` returning a restore handle, plus `CharacterSkinAnimationRenderer.apply(...)` for dispatch.

- [ ] **Step 1: Implement the model-part snapshot** for vanilla body/head/arms/legs and exact restoration in `CharacterSkinAnimationState.close()`.

- [ ] **Step 2: Implement rig evaluation** using the existing GeckoLib `fillRenderState`, `GeoModel.prepareForRenderPass`, and `GeoModel.handleAnimations`; set the rig model's render type to `null` so no Geo cubes are drawn.

- [ ] **Step 3: Map supported bones** with explicit conversion from GeckoLib's radians/position units to vanilla `ModelPart` rotations and positions. Ignore unsupported child-only bones and preserve vanilla arm poses when no action clip owns them.

- [ ] **Step 4: Add a renderer hook** that applies the adapter only for selected non-`NONE` players, allows vanilla `LivingEntityRenderer.render` to continue, and closes the state in `finally`.

- [ ] **Step 5: Run `testProjectSanity` and `compileClientJava`**. Expected: RED tests from Task 1 become GREEN and client compilation succeeds.

- [ ] **Step 6: Commit** with `feat(render): adapt geckolib clips to player skins`.

### Task 3: Wire all vessels and preserve vessel-specific animation state

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/character/CharacterClientDefinition.java`
- Modify: `src/client/java/jujutsu/mod/client/character/nobara/NobaraClientDefinition.java`
- Modify: `src/client/java/jujutsu/mod/client/character/todo/TodoClientDefinition.java`
- Modify: `src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java`
- Modify: `src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoRenderer.java`
- Modify: `src/client/java/jujutsu/mod/client/mixin/CharacterRenderDispatchMixin.java`
- Modify: `src/client/resources/jujutsumod.client.mixins.json`

**Interfaces:**
- Consumes: `CharacterSkinAnimationAdapter` from Task 2 and existing vessel definitions.
- Produces: one adapter binding per animated vessel, with `NONE` returning `null`; vanilla renderer dispatch for every player.

- [ ] **Step 1: Add `skinAnimation()` to `CharacterClientDefinition`** with a `null` default and bind Nobara, Todo, and Megumi to adapters that use their current animatables.

- [ ] **Step 2: Move Megumi's swing-variant bookkeeping** from the old visible Geo renderer into its skin adapter so punch/punch/kick selection remains unchanged.

- [ ] **Step 3: Replace the canceling Geo dispatch** with a narrow pre/post vanilla player render hook and remove only the obsolete `CharacterRenderDispatchMixin` registration. Keep `PlayerRenderContextMixin` only if the adapter needs the actual player instance.

- [ ] **Step 4: Run focused tests**: `./gradlew.bat testCharacterClients testProjectSanity --no-daemon --max-workers=1 --no-watch-fs`.

- [ ] **Step 5: Commit** with `refactor(render): wire vessel skin animation adapters`.

### Task 4: Archive legacy visible Blockbench player models

**Files:**
- Create: `archive/character-player-gecko/README.md`
- Create: `archive/character-player-gecko/manifest.txt`
- Move: old player Geo Java classes under `src/client/java/jujutsu/mod/client/render/{CharacterGeoRenderer,CharacterGeoRenderers,CharacterPlayerGeoModel,CharacterPlayerGeoRenderer,CharacterHeldItemLayer,nobara,todo,megumi}`
- Move: old visible player Geo models under `src/main/resources/assets/jujutsumod/geckolib/models/{projectjjk/nobara_kugisaki,todo/todo_aoi,megumi/megumi_fushiguro}.geo.json`
- Move: old visible player model textures `textures/projectjjk/entity/npcs/nobara_kugisaki.png`, `textures/entity/character/{todo_aoi,megumi_fushiguro}.png`

**Interfaces:**
- Consumes: the committed working runtime from Tasks 2-3.
- Produces: an explicit archive that is not included in the mod jar and a manifest of every moved legacy file.

- [ ] **Step 1: Move only files proven to belong to the replaced player-model path** and leave straw doll/Divine Dog Geo assets live.

- [ ] **Step 2: Write the archive manifest** with original paths, reason for archival, and note that animation JSON remains live because the new rig consumes it.

- [ ] **Step 3: Extend `processResources`/sanity checks** so `archive/**` cannot enter runtime resources and the old live paths cannot silently return.

- [ ] **Step 4: Run `./gradlew.bat testProjectSanity --no-daemon --max-workers=1 --no-watch-fs` and inspect `jar tf build/libs/jujutsumod-1.0.0.jar`**. Expected: archive is absent from the jar; skin PNGs, animation JSONs, and new rigs are present.

- [ ] **Step 5: Commit** with `chore(render): archive legacy player geo models`.

### Task 5: Update current architecture docs and verify

**Files:**
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/Vessel-render-stack.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/02-architecture/Assets-and-resources.md`
- Modify: `SESSION.md`

**Interfaces:**
- Consumes: actual implementation and archive manifest from Tasks 2-4.
- Produces: current docs describing skin-backed GeckoLib animation, archive boundary, and remaining manual smoke requirements.

- [ ] **Step 1: Replace claims that third-person vessels are rendered by visible Geo models** with the new adapter/vanilla PlayerRenderer contract.

- [ ] **Step 2: Record the active worktree, branch, commits, gate result, and manual smoke boundary in `SESSION.md`.**

- [ ] **Step 3: Run the complete gate**: `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs`.

- [ ] **Step 4: Run `./gradlew.bat runClient --no-daemon --max-workers=1 --no-watch-fs` long enough to inspect F5 skin rendering and each vessel action, then record whether the user-facing smoke is complete.**

- [ ] **Step 5: Commit** with `docs(render): document skin animation bridge`.

