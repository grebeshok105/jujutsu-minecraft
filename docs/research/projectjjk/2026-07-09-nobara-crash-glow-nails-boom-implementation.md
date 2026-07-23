# Nobara Crash / Glow / Nails / Boom Implementation

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

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
- `NobaraPlayerGeoRenderer` renders through GeckoLib with an extracted `AbstractClientPlayer`, the already-filled `PlayerRenderState`, and explicit GeckoLib render data; it no longer silently falls back to the old player skin on missing Gecko data.
- `ProjectJjkRitualRuntime.markTarget` applies `MobEffects.GLOWING`, and consumed marks clear that effect.
- The old target-mark S2C payload and client target-mark render manager were removed from the runtime path.
- Hairpin Enlarge and Hairpin Explosion send `ProjectJjkNobaraImpulsePayload.FP_SNAP` to the caster instead of reusing the hammer impulse.
- Client networking starts `FpSnapAnimator.playSnap`, and `NobaraFirstPersonSnapMixin` renders a short first-person main-hand snap pose.
- Embedded nails now store local body-space offset/forward data and use body rotation (`yBodyRot`) rather than look/head yaw.
- `ProjectJjkNailRenderer` render-attaches embedded nails to the host with partial-tick host position and interpolated body yaw.
- Hairpin Explosion search starts at the caster eye (`HAIRPIN_EXPLOSION_DETECT_FORWARD_OFFSET = 0.0`) and falls back to nearby owned embedded nails/marked targets.

## Follow-up Crash Fix

After the first jar install, the client crashed at `2026-07-09 14:39:54` with `IllegalStateException: Pose stack not empty` in `LevelRenderer.checkPoseStack`. The crash was a render-stack balance issue, not the earlier GeckoLib missing-model issue.

`NobaraPlayerGeoRenderer.renderNobara` now wraps the GeckoLib replacement render in a local `PoseStack` guard and restores the stack in `finally`. This keeps a GeckoLib replacement render from leaking an extra pose into Minecraft's world render pass.

## Follow-up Skin Fallback Fix

After the pose-stack fix, selecting Nobara no longer crashed but showed the old player-skin replacement instead of the new GeckoLib NPC body. Root cause: the custom render hook called GeckoLib `render(...)` directly on Minecraft's vanilla `PlayerRenderState`. GeckoLib 5 expects its data tickets to be filled by the replaced-entity pipeline before rendering; the old code caught the resulting `IllegalArgumentException` and returned `false`, so vanilla player rendering continued with `CharacterSkinMixin`.

The fix stores a weak client render context from `PlayerRenderer.extractRenderState`, resolves the current `AbstractClientPlayer` during the living render hook, runs `fillRenderState(getAnimatable(), player, state, partialTick)`, and adds `DataTickets.PACKED_LIGHT` before rendering. The old `IllegalArgumentException` catch was removed so a broken Gecko state cannot be hidden behind the old skin again.

## Follow-up Idle / Skirt Fix

After the Gecko body appeared in-game, the model stayed in the run pose while standing still and the rear/side clothing panel appeared detached. Root causes:

- `NobaraPlayerGeoAnimatable` used `HumanoidRenderState.speedValue` as a movement trigger. In vanilla player animation this field is a limb-animation scale/divisor, not proof that the entity is moving, and it can sit around `1.0`.
- The ProjectJJK `bb_main` clothing-panel bone is a root bone while the empty `skirt` bone is parented to `body`. In our player-replacement render path, that made the panels fail to follow the body pose.

The animation predicate now follows ProjectJJK's intent more closely by using GeckoLib movement data (`state.isMoving()`), real velocity, and sprinting data tickets. `bb_main` is parented to `skirt` in the GeckoLib 5 runtime copy of the model so the clothing panels inherit the body transform.

## Verification

- `gradlew.bat testProjectSanity testProjectJjkNobaraProfile --no-daemon` passed after the body-yaw and FP snap polish.
- `gradlew.bat testProjectSanity --no-daemon` first failed on the missing pose-stack guard, then passed after the guard was added.
- `gradlew.bat testProjectSanity --no-daemon` first failed on the missing Gecko render context, then passed after the player/partialTick context and `fillRenderState` path were added.
- `gradlew.bat testProjectSanity --no-daemon` first failed on the old movement/skirt contract, then passed after the movement predicate and `bb_main` parent fix.
- `gradlew.bat check --no-daemon` passed after all code changes.
- `gradlew.bat build --no-daemon -x test` passed and produced `build/libs/jujutsumod-1.0.0.jar`.
- The runtime jar was copied to `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`.
- The installed jar contains the GeckoLib 5 Nobara model and animation resources under `assets/jujutsumod/geckolib/**`.

## Remaining Risk

Compilation verifies the mixin signatures and resource wiring. It does not prove the exact visual alignment of the first-person snap or embedded nails in a live client; those still need an in-game smoke test after the updated jar is installed.
