# Character Skin Animation Bridge Design

## Goal

Render Nobara, Todo, and Megumi with their existing Minecraft player-skin PNGs while retaining the current GeckoLib animation clips, controllers, and action triggers.

## Scope

- Keep GeckoLib 5.2.2 as the animation runtime and keep the existing vessel-specific animatables and VFX trigger names.
- Replace the visible GeckoLib player geometry with the vanilla `PlayerModel`, so standard skin UVs, outer skin layers, armor layers, capes, and elytra continue through Minecraft's player renderer.
- Adapt the existing clips through the shared humanoid bones (`root`, `body`, `head`, `leftArm`, `rightArm`, `leftLeg`, and `rightLeg`). Unsupported Blockbench-only bones (`elbow`, `knee`, `hand`, `hair_bun`, and facial detail bones) are ignored or folded into their parent limb; they do not become a new geometry system.
- Preserve the existing first-person VFX channels and hand-specific treatments. The third-person bridge owns the body pose; first-person animation remains the existing camera/hand presentation.
- Move legacy visible player Geo Java/model/texture files into a root archive outside runtime source sets. Keep animation JSON resources live because the bridge consumes them.

## Architecture

Each vessel keeps its existing `GeoAnimatable` controller state and animation resource. A shared skin animation adapter prepares the GeckoLib render state, runs the current animation processor against a small invisible humanoid rig, reads the resulting bone transforms, and applies the supported transforms to the `PlayerModel` owned by vanilla `PlayerRenderer`.

The existing `LivingEntityRenderer` dispatch no longer cancels player rendering. Instead, a narrow render hook applies the selected vessel's adapted pose after vanilla `PlayerModel.setupAnim` and restores every touched `ModelPart` in a `finally` path after the vanilla render finishes. A selected `NONE` vessel and unselected players pass through untouched.

The adapter is definition-driven: `CharacterClientDefinition` supplies a skin animation adapter or `null`. Shared code resolves the adapter from the client definition and never switches on character names. Megumi's melee variant state remains in his vessel-specific animation adapter, where it is already tracked.

The rig contains no visible cubes and exists only so GeckoLib can evaluate the existing clips. Its bone names match the clips' shared names. The old Blockbench player geometry, old player Geo renderer/model classes, and their dedicated model textures are archived with their original relative paths and are not loaded by runtime resources.

## Transform policy

- Apply root/body/head/arm/leg rotation and supported position offsets to matching vanilla `ModelPart`s.
- Treat `left_elbow`, `right_elbow`, `left_knee`, `right_knee`, `left_hand`, `right_hand`, `hair_bun`, and facial bones as compatibility-only rig bones; their transforms do not create extra visible geometry.
- Preserve vanilla item pose only when no action clip owns the arms, matching the current `CharacterPlayerGeoModel` contract.
- Use the player's selected `PlayerSkin.Model` for the vanilla renderer. The skin files remain the current 64x64 wide-arm textures.
- Restore the exact pre-adaptation rotations, positions, visibility, and child-part state after the render call, preventing a selected vessel from leaking its pose into another player or another layer.

## Failure handling

If a selected vessel has no adapter, or the render context is unavailable, the vanilla player render continues with its ordinary pose. Animation processing must not cancel vanilla rendering or swallow unrelated rendering exceptions. The adapter uses a local pose snapshot and a `finally` restoration block.

## Verification

- Unit/structural tests prove all live vessel definitions either opt into the skin adapter or explicitly keep vanilla untouched, the rig resource ids exist, and legacy visible player Geo paths are absent from runtime source/resource trees but present under the archive.
- Focused compile/test runs cover the adapter's bone mapping and pose restoration with pure model-part fixtures where possible.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` is the final automated gate.
- A live client smoke is still required for F5 third-person skin rendering, idle/walk/run, each vessel action, held items, armor/cape visibility, and first-person hand effects. A green gate does not replace this smoke.
