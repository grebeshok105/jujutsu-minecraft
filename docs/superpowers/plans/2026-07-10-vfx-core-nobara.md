# VFX Core for Fabric 1.21.8 and Nobara — Implementation Plan

> **For agentic workers:** execute sequentially in the isolated `nobara-cinematic-slice` worktree. Use TDD for every new production type, make one conventional commit per completed task, and run the named verification before that commit.

**Goal:** Build a reusable, Fabric-native VFX library so a future agent adds a combat effect through a typed cue and a small Java recipe instead of modifying payload handlers, render events, HUD code, camera mixins, and particle helpers independently.

**Architecture:** Server-authoritative abilities emit a compact shared `VfxCue`. `VfxCuePayload` carries it to clients through the existing Fabric networking registration. The client-only `VfxDirector` resolves the cue ID to a Nobara recipe, owns timeline/lifecycle/quality decisions, and fans the scene out to world geometry, local particles, sound, HUD, camera, and first-person channels. Persistent nail aura remains in the nail entity renderer; it may reuse palette/geometry helpers but is not forced into a transient timeline.

**Tech stack:** Java 21, Minecraft 1.21.8, Fabric API 0.136.1+1.21.8, official Mojang mappings, existing Fabric payload networking, existing GeckoLib 5.2.2. No added dependency.

## Global Constraints

- Work only in `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice` on `codex/nobara-cinematic-slice`; do not modify the dirty main checkout.
- Use public Fabric APIs. Keep renderer/HUD/camera/input classes under `src/client`; gameplay and cue emission remain server-authoritative.
- Do not add Satin, Veil, Photon, JSON/DSL authoring, a preview/demo screen, broad mixins, or a generic GeckoLib bone-effect layer.
- Use `WorldRenderEvents.AFTER_ENTITIES` for transient world geometry; retain narrow existing mixins only where camera/first-person rendering already requires them.
- Default rendering targets spectacle. Distance culling and the vanilla particle setting may reduce density, never alter gameplay or networking semantics.
- Existing server particles stay as cheap shared combat feedback. Client recipes layer visual composition on top rather than replacing valid server feedback.
- Every meaningful code and VFX documentation change gets a conventional English commit. Never copy a runtime jar before a successful build.

## Public Contract

### Shared main-source types

```java
public record VfxCue(
    ResourceLocation effectId,
    Vec3 origin,
    int anchorEntityId,
    int intensity,
    long startGameTime,
    long seed
) {
    public static final int NO_ANCHOR = -1;
}
```

- `effectId` is a stable resource location such as `jujutsumod:nobara/enlarge`.
- `origin` is always present and is the fallback when an entity anchor disappears.
- `anchorEntityId` is an optional client-resolved entity ID; `NO_ANCHOR` means static world anchoring.
- `intensity` carries the small, bounded effect magnitude already represented by Nobara nail/mark counts.
- `startGameTime` makes late packets enter the correct timeline phase; expired scenes are ignored.
- `seed` is server-created and drives deterministic recipe variation. It does not promise pixel-identical output across different quality settings or frame rates.

`VfxCuePayload` serializes exactly the fields above. `JujutsuNetworking.broadcastVfxCue(ServerLevel, Vec3, double, VfxCue)` and `sendVfxCue(ServerPlayer, VfxCue)` are the only generic S2C VFX helpers.

### Client-only authoring API

```java
public interface VfxRecipe {
    VfxInstance create(VfxCue cue, VfxContext context);
}

public final class VfxDirector {
    public static void register(ResourceLocation effectId, VfxRecipe recipe);
    public static void receive(VfxCue cue);
}
```

- `VfxContext` exposes only director channels: world primitives, local particles, local sound, HUD, camera/FOV, and first-person motion.
- Recipes do not register render callbacks, send packets, mutate gameplay, or call the old Hairpin static managers.
- The director owns active instance lifetime, world/disconnect cleanup, quality selection, anchor resolution, and unknown-ID logging once per effect ID.
- Internal post-process integration is deliberately unavailable to recipes in v1. A later compatible shader spike may add a backend behind the director without changing cue transport.

### Nobara IDs and scenes

`NobaraVfxIds` centralizes IDs for `hammer`, `impact`, `impact_sound`, `resonance_channel`, `resonance_strike`, `link_bind`, `detonate`, `enlarge`, `explosion`, and `first_person_snap`.

The three reference scenes are:

1. **Hammer / launch:** forged-metal sound beat, short camera/HUD hit, cyan-white launch emphasis while real nail entities and server trails remain authoritative visuals.
2. **Resonance / link:** cursed-energy tether/burst, layered local particles, channel pulse, and readable target-origin timing.
3. **Enlarge / Boom:** expanding rings, ribbons/blades, particle burst, sound, screen flash, camera impulse, and caster-only first-person snap.

## File Structure

- Create `src/main/java/jujutsu/mod/vfx/VfxCue.java` and `NobaraVfxIds.java`: shared cue contract and IDs.
- Create `src/main/java/jujutsu/mod/network/VfxCuePayload.java`: typed S2C serializer.
- Modify `src/main/java/jujutsu/mod/network/JujutsuNetworking.java`: payload registration plus generic send/broadcast helpers.
- Modify `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java` and `ProjectJjkRitualRuntime.java`: emit named VFX cues after server-confirmed actions.
- Create `src/client/java/jujutsu/mod/client/vfx/`: director, recipe registry, timeline/instance lifecycle, quality policy, world/HUD/camera/first-person channels, and reusable ribbon/ring/blade primitives.
- Create `src/client/java/jujutsu/mod/client/vfx/nobara/`: Nobara palette and recipe registration.
- Modify `JujutsuModClient`, `JujutsuClientNetworking`, existing camera/first-person mixins, and `ProjectJjkNailRenderer` only as needed to use the generic runtime.
- Delete the superseded VFX-only `ProjectJjkNobaraImpulsePayload` and Hairpin static manager classes after all references are migrated; preserve unrelated character-selection networking.
- Create `src/test/java/jujutsu/mod/vfx/VfxCueTest.java` and `VfxTimelineTest.java`; modify `build.gradle` to register `testVfxCore`; adjust `ProjectSanityTest` guards from old payload/static-manager expectations to the new contract.
- Create/update the Obsidian VFX library note and MOC; update `Hairpin-effects`, networking, client/server boundaries, public API surface, lifecycle, and stale legacy references.

## Task 1: Persist the Design and Handoff

**Files:** this plan and `docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md`.

- [ ] Record the approved scope, source-of-truth worktree, rejected dependencies, public contract, test/QA rules, and suggested skills for a new session.
- [ ] Confirm both documents contain no secrets and point to the current branch/worktree.
- [ ] Commit: `docs(vfx): add core implementation plan`.

## Task 2: Shared Cue Transport (TDD)

**Files:** `VfxCue.java`, `NobaraVfxIds.java`, `VfxCuePayload.java`, `JujutsuNetworking.java`, `build.gradle`, `VfxCueTest.java`.

- [ ] Write failing assertion tests for cue field preservation, `NO_ANCHOR`, stable Nobara IDs, and binary payload round-trip.
- [ ] Register and run `testVfxCore`; verify the failure is caused by missing VFX types, not test setup.
- [ ] Implement the smallest shared cue, payload codec, payload registration, and canSend-gated direct/radius sends needed to make those tests pass.
- [ ] Replace only server-side `ProjectJjkNobaraImpulsePayload` construction sites with named cues. Keep gameplay timing, damage, particles, and sound authority unchanged.
- [ ] Run `gradlew.bat testVfxCore --no-daemon` and `gradlew.bat check --no-daemon`.
- [ ] Commit: `feat(vfx): add synchronized effect cues`.

## Task 3: Client Director and Rendering Channels (TDD)

**Files:** new `client/vfx/**`, client initialization, existing camera/HUD/first-person integration, `VfxTimelineTest.java`.

- [ ] Write failing pure timeline tests for age calculation, late cues, expiry, and no-anchor fallback before director code.
- [ ] Implement an active-instance director with bounded queue, per-cue distance/particle quality, world/disconnect cleanup, and safe ignore-on-unknown-ID behavior.
- [ ] Implement reusable world ring/ribbon/blade rendering at `AFTER_ENTITIES`; reuse the current camera-relative geometry approach instead of introducing a shader or renderer mixin.
- [ ] Implement client channels for local particle bursts, sound, HUD, camera/FOV, and first-person motion. Retarget existing narrow mixins to the new generic channels; add no mixins.
- [ ] Run the VFX tests and `gradlew.bat compileClientJava --no-daemon` after every red-green slice.
- [ ] Commit: `feat(vfx): add client effect director`.

## Task 4: Nobara Recipe Migration and Polish (TDD)

**Files:** `client/vfx/nobara/**`, Nobara runtime/ritual emitters, client networking, old VFX managers, nail renderer, `ProjectSanityTest`.

- [ ] Write/adjust failing guards proving every Nobara cue ID has a registered recipe and that the client network receiver delegates cues to the director rather than branching on legacy integers.
- [ ] Register all ten Nobara recipes and compose each through director channels.
- [ ] Preserve the real nail entity renderer and server particles; extract shared cyan/white palette or primitive helpers only where it removes VFX duplication.
- [ ] Migrate `FP_SNAP` to a direct caster cue, preserving the non-cancelling first-person hand mixin behavior.
- [ ] Remove `ProjectJjkNobaraImpulsePayload`, `HairpinWorldRenderer`, `HairpinCinematicCamera`, `HairpinScreenOverlay`, `ResonanceEffects`, and `FpSnapAnimator` only after no references remain; do not remove character-selection networking.
- [ ] Run `gradlew.bat testVfxCore testProjectSanity --no-daemon`, then `gradlew.bat check --no-daemon`.
- [ ] Commit: `refactor(nobara): route combat effects through vfx core`.

## Task 5: Documentation, Build, and Real-Game QA

**Files:** Obsidian VFX notes/MOC and source-backed architecture notes; no unrelated docs.

- [ ] Document the agent authoring path: ID → cue → recipe → verification. Explicitly forbid direct ability-to-renderer coupling and client gameplay mutation.
- [ ] Update old documentation that still claims removed `HairpinTimeline`, `HairpinVisualProfile`, or playback classes are live.
- [ ] Run `gradlew.bat check --no-daemon` and `gradlew.bat build --no-daemon -x test`.
- [ ] Run `gradlew.bat runClient --no-daemon`; test the real Nobara hammer/launch, resonance/link, Enlarge/Boom, death/despawn anchor fallback, and reduced particle settings.
- [ ] Perform a two-client manual smoke when the local instance setup allows it: one player casts and one observes a single server-confirmed scene with no client gameplay authority.
- [ ] Copy the final non-dev/non-sources jar from `build/libs` to `D:\Games\instances\Jujutsu\mods`, replacing the installed `jujutsumod` jar; verify the copied file exists and matches the built artifact.
- [ ] Commit documentation-only changes: `docs(vfx): document nobara core migration`.

## Acceptance Criteria

- A future character can add a VFX event by defining a resource ID, emitting `VfxCue`, registering one Java recipe, and documenting the recipe—without editing a packet switch, renderer event registration, or mixin.
- Every current Nobara visual impulse travels through `VfxCuePayload` and `VfxDirector`; no legacy integer impulse payload or old Hairpin static manager remains.
- Server-only combat state remains server-authoritative; cue packets produce visuals only.
- Late, unknown, expired, disconnected, and missing-anchor cues fail safely without a crash or persistent ghost effect.
- `check` and runtime-jar build pass; the installed instance receives the exact built jar.

## Deferred Work

- Custom `RenderPipeline`/post-process shader spike after a compatible Fabric 1.21.8 route is independently proven.
- JSON/data-driven authoring, GUI effect editor, VFX preview command, generic GeckoLib bone attachments, and generalized cinematic UI.
