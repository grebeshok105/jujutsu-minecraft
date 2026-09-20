# Block 1 report — real 3D Nue wings

## Done

- Added `megumi_nue_wings.geo.json` with a `wings_root` hierarchy and four extracted wing bones. The two source zero-depth planes now have one-pixel depth; the renderer applies the requested 0.55 player-scale.
- Added `megumi_nue_wings.animation.json` clips: `materialize`, `folded_idle`, `unfold`, `fly`, `fold`, and `dissolve`.
- Derived `megumi_nue_wings.png` from the non-transparent Nue wing atlas islands while retaining the 256x256 UV layout.
- Added C1 `MegumiWingsStatePayload`, `MegumiWingsSync.send`, and `MegumiWingsSync.sendInactive`. The sender retains phase start game time across heartbeat sends and broadcasts to tracking players plus the owner.
- Added `MegumiWingsState` with wire-phase mapping, clip mapping, normalized progress, stale-heartbeat cleanup, client tick cleanup, disconnect clear, and inactive folding teardown.
- Added `MegumiWingsModel`, `MegumiWingsGeoRenderer`, and `MegumiWingsLayer`. The vanilla layer resolves the player from `PlayerRenderState.id`, copies the prepared body transform, offsets to the back, and renders only when a phase exists.
- Added `PlayerRendererWingsMixin`; it injects at the `PlayerRenderer` constructor tail so both default and slim renderers receive the layer.
- Added `MegumiPartialPayloads.registerAll()` registering only the wings payload.
- Removed the obsolete `NUE_WINGS=SUMMON` alias and `triggerNueWings` hook.
- Added `MegumiWingsResourcesTest`, `MegumiWingsPhaseTest`, and `MegumiWingsPayloadCodecTest`.

## Files touched

- New assets: `src/main/resources/assets/jujutsumod/geckolib/models/megumi_nue_wings.geo.json`, `src/main/resources/assets/jujutsumod/geckolib/animations/megumi_nue_wings.animation.json`, `src/main/resources/assets/jujutsumod/textures/entity/megumi_nue_wings.png`
- New client code: `src/client/java/jujutsu/mod/client/render/megumi/MegumiWingsModel.java`, `MegumiWingsGeoRenderer.java`, `MegumiWingsLayer.java`, `src/client/java/jujutsu/mod/client/character/megumi/MegumiWingsState.java`, `src/client/java/jujutsu/mod/client/mixin/PlayerRendererWingsMixin.java`
- New server/network code: `src/main/java/jujutsu/mod/network/MegumiWingsStatePayload.java`, `MegumiPartialPayloads.java`, `src/main/java/jujutsu/mod/character/megumi/MegumiWingsSync.java`
- Existing edits: `MegumiPlayerGeoAnimatable.java`, `MegumiAnimationHooks.java`
- Tests: `src/test/java/jujutsu/mod/client/render/megumi/MegumiWingsResourcesTest.java`, `MegumiWingsPhaseTest.java`, `src/test/java/jujutsu/mod/network/MegumiWingsPayloadCodecTest.java`

## Preview evidence

- The derived wing texture was opened and visually checked after masking the source atlas; it contains the two wing texture islands and transparent unused atlas space.
- Static JSON/image inspection confirmed: bones are `wings_root`, `right_wing`, `right_wing_tip`, `left_wing`, `left_wing_tip`; all four cube depths are `1.0`; all six lifecycle clip ids exist; the output texture is 256x256 with non-transparent wing islands.
- The resource test includes the repeatable UV-pixel evidence check for the tip and root wing islands.

## Deviations

- The texture remains 256x256 rather than being cropped, because the extracted geometry keeps the source UV coordinates; only the two non-transparent wing atlas islands are copied into the derived texture.

## Unresolved / integration-owned

- `JujutsuNetworking.java` wiring of `MegumiPartialPayloads.registerAll()` is intentionally left to B5 per C6.
- `jujutsumod.client.mixins.json` entry for `PlayerRendererWingsMixin` is intentionally left to B5 per C6.
- `MegumiPartialClientInit` receiver/tick/disconnect wiring is intentionally left to B2; call `MegumiWingsState.apply`, `MegumiWingsState.tick`, and `MegumiWingsState.clear` there.
- `MegumiVfxRecipes` removal of the obsolete `triggerNueWings` caller is intentionally left to B3, its file owner.

## Verification for Main

Run: `gradlew.bat compileClientJava test --tests "*MegumiWings*"`
Expected: `BUILD SUCCESSFUL`; all `MegumiWings*` tests pass, including resource geometry/depth/UV checks, phase mapping, and payload active/inactive codec round-trips.

No Gradle, test, GameTest, qualityGate, or MCP lane commands were run in this worker session per the block ownership constraint.
