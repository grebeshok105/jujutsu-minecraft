# Nobara Crash / Glow / Nails / Boom Implementation

Date: 2026-07-09

## Scope

This pass implements the crash/glow/nails/boom/snap fixes from the maintenance audit, treating that audit as a hypothesis and verifying against the current cinematic worktree.

## Root Causes

- F5 crash: GeckoLib 5.2.2 did not bake the old `assets/jujutsumod/geo/**` layout used by the ProjectJJK 1.21.1 assets.
- Target mark: our custom world shell was not the requested vanilla Minecraft Glowing effect.
- Enlarge/Boom cast feel: the finisher path reused the hammer/anvil impulse instead of a snap-only first-person event.
- Embedded nails: the old placement was surface-biased and visually chased the target entity instead of render-attaching to the host with partial ticks.
- Boom flakiness: the search capsule started four blocks in front of the caster eye, so close/near-foot anchors could be cut off.

## Implemented

- Nobara GeckoLib assets are available under the GeckoLib 5 layout:
  - `assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json`
  - `assets/jujutsumod/geckolib/animations/projectjjk/npc.animation.json`
- `NobaraPlayerGeoModel` now uses stripped GeckoLib resource keys: `projectjjk/nobara_kugisaki` and `projectjjk/npc`.
- `NobaraPlayerGeoRenderer` falls back to vanilla player rendering if GeckoLib still throws while resolving the model.
- `ProjectJjkRitualRuntime.markTarget` applies `MobEffects.GLOWING`, and consumed marks clear that effect.
- The old target-mark S2C payload and client target-mark render manager were removed from the runtime path.
- Hairpin Enlarge and Hairpin Explosion send `ProjectJjkNobaraImpulsePayload.FP_SNAP` to the caster instead of reusing the hammer impulse.
- Client networking starts `FpSnapAnimator.playSnap`, and `NobaraFirstPersonSnapMixin` renders a short first-person main-hand snap pose.
- Embedded nails now store local body-space offset/forward data and use body rotation (`yBodyRot`) rather than look/head yaw.
- `ProjectJjkNailRenderer` render-attaches embedded nails to the host with partial-tick host position and interpolated body yaw.
- Hairpin Explosion search starts at the caster eye (`HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET = 0.0`) and falls back to nearby owned embedded nails/marked targets.

## Verification

- `gradlew.bat testProjectSanity testProjectJjkNobaraProfile --no-daemon` passed after the body-yaw and FP snap polish.
- `gradlew.bat check --no-daemon` passed after all code changes.
- `gradlew.bat build --no-daemon -x test` passed and produced `build/libs/jujutsumod-1.0.0.jar`.
- The runtime jar was copied to `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`.
- The installed jar contains the GeckoLib 5 Nobara model and animation resources under `assets/jujutsumod/geckolib/**`.

## Remaining Risk

Compilation verifies the mixin signatures and resource wiring. It does not prove the exact visual alignment of the first-person snap or embedded nails in a live client; those still need an in-game smoke test after the updated jar is installed.
