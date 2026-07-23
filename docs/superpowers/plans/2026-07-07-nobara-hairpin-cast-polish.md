# Nobara Hairpin Cast Polish Implementation Plan

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Nobara Hairpin read as four prepared nails struck by one heavy hammer hit, with blue cursed-flame flight, target attachment, layered impact sounds, and stronger cinematic focus.

**Architecture:** Keep the existing item-first and server-authoritative runtime. Add deterministic placement helpers in `HairpinGameplayService`, send stable prepared nail positions through payloads, and upgrade the existing client renderer/camera/overlay/sound hooks instead of adding entities or dependencies.

**Tech Stack:** Fabric API for Minecraft `1.21.8`, Java `21`, Mojang mappings, existing custom payload networking, existing world renderer and HUD overlay.

## Global Constraints

- Keep the existing item-first flow.
- Keep server-authoritative target resolution, damage, knockback, cooldown, and impact timing.
- Do not add temporary nail entities or per-nail world hitboxes in this pass.
- Do not add a new visual dependency.
- Do not run automated in-game smoke tests; the user will verify in-game manually.

---

## File Structure

- Modify `src/main/java/jujutsu/mod/character/nobara/HairpinGameplayService.java`: deterministic prepared row placement and reuse for launch starts.
- Modify `src/main/java/jujutsu/mod/network/PreparedNailsPayload.java`: include four prepared nail positions.
- Modify `src/main/java/jujutsu/mod/character/nobara/NobaraHairpinRuntime.java`: compute row positions at prepare and launch, layer launch/impact sounds, keep target resolution server-side.
- Modify `src/client/java/jujutsu/mod/client/fx/NobaraNailFlightManager.java`: expose prepared nail positions from payload.
- Modify `src/client/java/jujutsu/mod/client/fx/HairpinWorldRenderer.java`: render row nails and blue cursed-flame ribbons/tongues.
- Modify `src/client/java/jujutsu/mod/client/fx/HairpinCinematicCamera.java`: add stronger launch/impact FOV and camera impulses.
- Modify `src/client/java/jujutsu/mod/client/fx/HairpinScreenOverlay.java`: add focus/vignette treatment for launch and impact.
- Modify `src/client/java/jujutsu/mod/client/fx/HairpinPlayback.java`: layer per-nail impact/ignition sound beats.
- Modify tests under `src/test/java/jujutsu/mod/character/nobara/`: lock row placement math and state behavior.

### Task 1: Stable Prepared Row Data

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/HairpinGameplayService.java`
- Modify: `src/main/java/jujutsu/mod/network/PreparedNailsPayload.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/NobaraHairpinRuntime.java`
- Test: `src/test/java/jujutsu/mod/character/nobara/HairpinGameplayServiceTest.java`

**Interfaces:**
- Produces: `HairpinGameplayService.preparedNailRow(Vec3 origin, Vec3 look, int nailCount): List<Vec3>`
- Produces: `PreparedNailsPayload` with four nail position triples.
- Consumes: existing `NobaraHairpinRuntime.prepareNails`.

- [ ] Add a test asserting four prepared nails form a readable row in front of the caster.
- [ ] Implement `preparedNailRow`.
- [ ] Extend `PreparedNailsPayload` read/write with four nail positions.
- [ ] Update `NobaraHairpinRuntime.prepareNails` to send those positions.
- [ ] Run `cmd.exe /c gradlew.bat testHairpinGameplayService --no-daemon`.
- [ ] Commit as `feat(gameplay): send prepared Hairpin nail row`.

### Task 2: Launch From Row Into Target

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/HairpinGameplayService.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/NobaraHairpinRuntime.java`
- Test: `src/test/java/jujutsu/mod/character/nobara/HairpinGameplayServiceTest.java`

**Interfaces:**
- Consumes: `preparedNailRow`.
- Produces: launch payloads whose nail starts match the prepared row.

- [ ] Update launch start computation to use the same row geometry.
- [ ] Keep entity/block/miss target resolution unchanged.
- [ ] Add/adjust tests so the row is deterministic and count-clamped to four.
- [ ] Run `cmd.exe /c gradlew.bat testHairpinGameplayService testNobaraCombatStateManager --no-daemon`.
- [ ] Commit as `feat(gameplay): launch Hairpin from prepared row`.

### Task 3: Blue Cursed-Flame Flight Visuals

**Files:**
- Modify: `src/client/java/jujutsu/mod/client/fx/NobaraNailFlightManager.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinWorldRenderer.java`

**Interfaces:**
- Consumes: `PreparedNailsPayload` nail positions.
- Consumes: existing `HairpinNailFlightPayload`.

- [ ] Render prepared nails from payload positions instead of orbiting around the player.
- [ ] Replace the plain blue flight strip with layered blue-white core, dark-blue outer ribbon, and short flickering flame tongues.
- [ ] Keep renderer math local to `HairpinWorldRenderer`; no new render dependency.
- [ ] Run `cmd.exe /c gradlew.bat compileJava compileClientJava --no-daemon`.
- [ ] Commit as `feat(vfx): render cursed-flame Hairpin nails`.

### Task 4: Cinematic Weight And Sound Layering

**Files:**
- Modify: `src/main/java/jujutsu/mod/character/nobara/NobaraHairpinRuntime.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinCinematicCamera.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinScreenOverlay.java`
- Modify: `src/client/java/jujutsu/mod/client/fx/HairpinPlayback.java`

**Interfaces:**
- Consumes: existing `JujutsuSounds` events.
- Produces: heavier launch/impact feel via layered existing sounds and stronger overlay/camera beats.

- [ ] Add server-side layered launch and impact sound calls using existing sound events with varied volume/pitch.
- [ ] Increase launch FOV punch and impact recoil in `HairpinCinematicCamera`.
- [ ] Add a focused vignette/defocus-style overlay beat in `HairpinScreenOverlay`.
- [ ] Layer short per-nail impact ticks in `HairpinPlayback`.
- [ ] Run `cmd.exe /c gradlew.bat compileJava compileClientJava --no-daemon`.
- [ ] Commit as `feat(vfx): add Hairpin cinematic hit weight`.

### Task 5: Package For Manual QA

**Files:**
- Modify only if compile/build exposes required fixes.

**Interfaces:**
- Produces: fresh `build/libs/jujutsumod-1.0.0.jar`.
- Produces: copied jar at `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.

- [ ] Run `cmd.exe /c gradlew.bat check build --no-daemon -x test`.
- [ ] Copy `build/libs/jujutsumod-1.0.0.jar` to `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar`.
- [ ] Verify the copied jar timestamp/size.
- [ ] Commit any build-fix-only source changes if needed.

## Self-Review

- Spec coverage: row prepare, one hammer launch, entity/no-target aim, cursed flame, camera/overlay/sound weight, and no automated client smoke are covered.
- Type consistency: new helper and payload fields are consumed by runtime and client renderer only.
