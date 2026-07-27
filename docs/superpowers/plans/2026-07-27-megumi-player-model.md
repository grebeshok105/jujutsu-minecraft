# Megumi Player Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the approved Megumi GeckoLib player body, locomotion, three-clip melee cycle, and server-confirmed Divine Dogs summon animation to PR #16.

**Architecture:** Megumi opts into the existing replaced-player renderer through his client definition. A Megumi-only renderer records each player's swing edge in a weak map and publishes a render ticket; the animatable selects the stable melee clip and uses an action controller for the summon cue.

**Tech Stack:** Java 21, Fabric 1.21.8, GeckoLib, JUnit 5, Blockbench runtime exports.

## Global Constraints

- No new payload, mixin, shared dispatch branch, gameplay state, or server-side melee behavior.
- Runtime assets only; do not commit previews or the `.bbmodel` source.
- Use the 128 x 128 archive texture for the GeckoLib body and the supplied 64 x 64 skin for first-person hands and roster.
- Keep `combat_idle` exported but unrouted.
- Finish with `./gradlew.bat qualityGate --no-daemon` and in-game smoke remains mandatory.

---

### Task 1: Export And Validate Runtime Assets

**Files:**
- Create: `src/main/resources/assets/jujutsumod/geckolib/models/megumi/megumi_fushiguro.geo.json`
- Create: `src/main/resources/assets/jujutsumod/geckolib/animations/megumi/megumi_fushiguro.animation.json`
- Create: `src/main/resources/assets/jujutsumod/textures/entity/character/megumi_fushiguro.png`
- Create: `src/main/resources/assets/jujutsumod/textures/entity/character/megumi.png`
- Create: `src/test/java/jujutsu/mod/client/character/megumi/MegumiPlayerPresentationTest.java`

**Interfaces:**
- Produces model bones `head`, `rightArm`, `leftArm`, `right_elbow`, `left_elbow`, `right_hand`, and `left_hand`.
- Produces the eight `animation.megumi_fushiguro.*` clips named in the design.

- [ ] Write a JUnit resource test that parses both JSON files with Gson, asserts required bones and all eight animation names, and asserts the two PNG dimensions.
- [ ] Run `./gradlew.bat test --tests "jujutsu.mod.client.character.megumi.MegumiPlayerPresentationTest" --no-daemon`; record the missing-resource RED result.
- [ ] Export the `.bbmodel` geometry and animations into the indexed GeckoLib roots and copy the two textures to their distinct destinations.
- [ ] Re-run the focused JUnit test and require PASS.

### Task 2: Add Megumi Renderer And Three-Clip Melee Route

**Files:**
- Create: `src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoRenderer.java`
- Create: `src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoModel.java`
- Create: `src/client/java/jujutsu/mod/client/render/megumi/MegumiPlayerGeoAnimatable.java`
- Create: `src/client/java/jujutsu/mod/client/render/megumi/MegumiHeldItemLayer.java`
- Modify: `src/test/java/jujutsu/mod/client/character/megumi/MegumiPlayerPresentationTest.java`

**Interfaces:**
- `MegumiPlayerGeoRenderer.addRenderData(...)` calls `super`, detects a swing rising edge per `AbstractClientPlayer` in a `WeakHashMap`, and writes `MegumiPlayerGeoAnimatable.MELEE_VARIANT` as `0`, `1`, or `2`.
- `MegumiPlayerGeoAnimatable` maps those indices to `punch_1`, `punch_2`, and `kick`, and exposes `triggerSummon(Entity)`.

- [ ] Extend the JUnit test to assert the route order and stable modulo-three cycle; record a transposed-route RED result.
- [ ] Implement the four Megumi-only render classes using the existing Todo/Nobara stack, held-item bones, movement thresholds, and head-look damping.
- [ ] Run `./gradlew.bat compileClientJava test --tests "jujutsu.mod.client.character.megumi.MegumiPlayerPresentationTest" --no-daemon` and require PASS.

### Task 3: Wire Skin, Renderer, And Confirmed Summon Animation

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/character/megumi/MegumiClientDefinition.java`
- Create: `src/client/java/jujutsu/mod/client/character/megumi/MegumiAnimationHooks.java`
- Modify: `src/client/java/jujutsu/mod/client/character/megumi/vfx/MegumiVfxRecipes.java`
- Modify: `src/main/java/jujutsu/mod/character/megumi/MegumiSummonRuntime.java`
- Modify: `src/test/java/jujutsu/mod/client/character/megumi/MegumiPlayerPresentationTest.java`

**Interfaces:**
- `MegumiClientDefinition.createRenderer` returns `new MegumiPlayerGeoRenderer<>(context)` and `playerSkin()` returns `textures/entity/character/megumi.png`.
- `MegumiAnimationHooks.triggerDivineDogs(VfxCue)` resolves only `cue.anchorEntityId()` and calls the animatable.
- Successful summon emits `DOGS_SUMMON` with `player.getId()`; recall and Sic behavior remain unchanged.

- [ ] Add assertions for renderer/skin declarations and the summon cue caster anchor; record a `NO_ANCHOR` RED result.
- [ ] Implement the definition, hook, recipe opening-beat trigger, and caster anchor.
- [ ] Run focused presentation, architecture, character definition, and character client checks and require PASS.

### Task 4: Documentation, Gate, Commit, And Push

**Files:**
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Megumi-Divine-Dogs.md`
- Modify: `AGENTS.md`
- Modify: `SESSION.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` when documentation metrics change.

- [ ] Record the custom body, animation mapping, source distinction, and smoke boundary in current docs.
- [ ] Run `./gradlew.bat qualityGate --no-daemon` and require `BUILD SUCCESSFUL`.
- [ ] Audit `git diff origin/main...HEAD`, payload count, mixin count, and `JujutsuCharacter.MEGUMI` production references.
- [ ] Commit as `feat(megumi): add animated player model` and push only `codex/megumi-divine-dogs` to update PR #16.
