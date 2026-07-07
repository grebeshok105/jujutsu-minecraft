# ProjectJJK Nobara Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a separate ProjectJJK-inspired Nobara comparison path with imported assets, world-anchored nails, fast hammer launch, and stronger audio/camera/VFX.

**Architecture:** Add a parallel runtime under the existing Nobara package rather than replacing the current Hairpin slice. Use a small server-owned nail entity plus existing client world-render/camera/overlay hooks for comparison visuals.

**Tech Stack:** Fabric API for Minecraft `1.21.8`, Java `21`, Mojang mappings, existing item/network/client split, ProjectJJK assets copied under the `jujutsumod` namespace.

## Global Constraints

- Keep the current Nobara items and behavior intact.
- Add ProjectJJK comparison items with separate ids.
- Use ProjectJJK assets only under `assets/jujutsumod/**/projectjjk/...`.
- Do not copy ProjectJJK Java code, mixins, access wideners, bundled jars, or dependencies.
- Do not add GeckoLib in this pass.
- Keep server-authoritative gameplay.

---

### Task 1: Design And Assets

**Files:**
- Create: `docs/superpowers/specs/2026-07-07-projectjjk-nobara-comparison-design.md`
- Create: `docs/superpowers/plans/2026-07-07-projectjjk-nobara-comparison.md`
- Create/Copy: `src/main/resources/assets/jujutsumod/projectjjk/**`
- Modify: `src/main/resources/assets/jujutsumod/sounds.json`

**Interfaces:**
- Produces runtime asset paths under `jujutsumod:projectjjk/...`.
- Produces sound event ids for ProjectJJK comparison sounds.

- [ ] Write this spec and plan.
- [ ] Copy selected ProjectJJK Nobara, VFX, spirit reference, and sound assets into `assets/jujutsumod/projectjjk`.
- [ ] Register only the sounds needed for the comparison runtime.
- [ ] Run `cmd.exe /c "set JAVA_HOME=D:\WorkFlow\Minecraft\jdk-21.0.11+10&& gradlew.bat testProjectSanity --no-daemon"`.
- [ ] Commit as `docs(nobara): plan ProjectJJK comparison slice` or `feat(assets): import ProjectJJK comparison assets`.

### Task 2: Server World Nails

**Files:**
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailEntity.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNailItem.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkHammerItem.java`
- Create: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java`
- Modify: `src/main/java/jujutsu/mod/registry/JujutsuItems.java`
- Create/Modify: entity registry as needed.
- Test: `src/test/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfileTest.java`

**Interfaces:**
- Produces ProjectJJK item ids and a server-owned nail entity.
- Produces deterministic hold-to-count thresholds: tap = 1, 300 ms = 3, 800 ms = 8.

- [ ] Write failing profile tests for hold thresholds, max nail age, and launch speed constants.
- [ ] Implement profile constants and item registration.
- [ ] Implement nail entity prepare/launch/impact lifecycle.
- [ ] Implement runtime methods for prepare and hammer launch.
- [ ] Run targeted tests and compile.
- [ ] Commit as `feat(nobara): add ProjectJJK comparison world nails`.

### Task 3: Client Comparison Rendering And Hit Feel

**Files:**
- Create: `src/client/java/jujutsu/mod/client/render/ProjectJjkNailRenderer.java`
- Modify: `src/client/java/jujutsu/mod/client/JujutsuModClient.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinCinematicCamera.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinScreenOverlay.java`
- Modify networking if an explicit client impulse payload is needed.

**Interfaces:**
- Consumes ProjectJJK nail entity type and item model.
- Produces immediate hammer hit camera/overlay impulse.

- [ ] Register a client entity renderer for ProjectJJK nails.
- [ ] Render the same ProjectJJK nail item model for item and world nail.
- [ ] Add a public ProjectJJK hammer impulse method to camera/overlay hooks.
- [ ] Trigger strong local impulse when the hammer launch begins.
- [ ] Run `compileJava compileClientJava`.
- [ ] Commit as `feat(client): render ProjectJJK Nobara nail impact`.

### Task 4: Verification And Review

**Files:**
- Modify only fixes found by verification/review.

**Interfaces:**
- Produces a verified dev jar copied to the configured instance.

- [ ] Run `cmd.exe /c "set JAVA_HOME=D:\WorkFlow\Minecraft\jdk-21.0.11+10&& gradlew.bat check --no-daemon"`.
- [ ] Run `cmd.exe /c "set JAVA_HOME=D:\WorkFlow\Minecraft\jdk-21.0.11+10&& gradlew.bat build --no-daemon -x test"`.
- [ ] Copy the jar to `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar` if the instance path exists.
- [ ] Run final code review and fix Critical/Important findings.
