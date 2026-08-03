# Session Handoff - Character Skin Animation

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/character-skin-animation`
- Branch: `codex/character-animation-overhaul`
- Base: `ff2ebb2` (`codex/character-skin-animation`, stacked on PR #52)
- Scope: replace the visible Nobara, Todo and Megumi player Geo models with ordinary Minecraft skins while retaining GeckoLib as the third-person animation runtime.

## Completed commits

- `c678f22` — design and implementation plan for the skin animation bridge
- `0c4de1c` — RED contract test for adapters, rigs, vanilla hook and archive boundary
- `c046cfd` — GeckoLib-to-vanilla skin animation bridge, vessel bindings, legacy player Geo archive and updated focused tests
- `a3ce412` — current Codex and session handoff updates
- `a014d28` — GeckoLib-compatible zero pivot/rotation defaults for every invisible skin rig and regression coverage
- `4c9e4c6` — review fixes: GeckoLib state guard, quaternion pose composition, targeted restoration state, bridge behavior tests and Megumi head policy cleanup
- `de5cd8a` — refresh the maintained Codex test-file metric after adding bridge coverage
- `d06eaae` — provide player movement and sprint tickets before GeckoLib evaluates skin clips
- `e00155f` — bind slim/classic skins, Todo's 1.15 body scale and dimensions/render hooks
- `ed71068` — archive the superseded skin rigs and animation packs
- `741e523` — replace third-person clips with skin-backed GeckoLib animation packs and add server-confirmed caster anchors

## Current implementation

- Vanilla `PlayerRenderer` and `PlayerModel` remain responsible for visible player geometry, skin UVs, outer skin layers, armor, capes, elytra and held items.
- `CharacterSkinAnimationMixin` applies a selected vessel's pose after vanilla `EntityModel.setupAnim`, wraps the vanilla render, and restores the seven bridge-owned `PlayerModel` parts in `finally`.
- `CharacterSkinAnimationAdapter` uses GeckoLib `fillRenderState`, `GeoModel.handleAnimations` and invisible bone-only rigs under `geckolib/models/character_skin/`. It checks the runtime `GeoRenderState` augmentation, provides the live player's `VELOCITY` and `SPRINTING` tickets plus vessel-specific data before controller evaluation, composes converted parent-to-child transforms with quaternions in vanilla `ZYX` order, and remains fail-loud for malformed live rigs or clips after restoring the snapshot.
- `CharacterSkinAnimationState` restores position, rotation, scale, visibility and `skipDraw` for root, body, head, both arms and both legs; executable tests cover restoration/idempotent close and the transform conversion/composition contract.
- Every rig bone declares three-component zero `pivot` and `rotation` arrays because GeckoLib 5.2.2 reads both fields unconditionally during resource baking.
- The live packs are newly authored for the invisible vanilla-skin rigs: Nobara has alternate idle/walk loops, a run loop, a three-clip melee sequence, ability and hammer clips; Todo has alternate idle/walk loops, a dedicated run, an attack trigger and a full `ability.boogie_woogie` clap; Megumi has a dedicated run, `combat_idle`, punch/punch/kick cycling and `summon_divine_dogs`.
- Nobara, Todo and Megumi definitions provide their own adapters through `skinAnimation()`; NONE inherits the null adapter and remains ordinary vanilla.
- Skin model variants are `NOBARA=slim` and `TODO/MEGUMI=wide`; Todo's `1.15f` body scale is applied only to dimensions and third-person rendering, never reach, damage or speed.
- Megumi's per-player swing variant sequence is owned by `MegumiSkinAnimationAdapter`.
- Megumi intentionally keeps procedural head-look disabled so his head follows the authored clip direction; the unused animatable-level head-look helper was removed.
- Old visible player Geo Java classes, models, textures and the canceling dispatch mixin remain under `archive/character-player-gecko/`. The superseded skin rigs and packs remain under `archive/character-skin-animation/`; only the new `geckolib/models/character_skin` rigs and `geckolib/animations/{projectjjk,todo,megumi}` packs are runtime resources.

## Verification

- `./gradlew.bat testProjectSanity --no-daemon --max-workers=1 --no-watch-fs` — passed after adding explicit rig rotations.
- `./gradlew.bat test --tests 'jujutsu.mod.vfx.VfxCueTest' --tests 'jujutsu.mod.client.vfx.VfxRadiusContractTest' --tests 'jujutsu.mod.client.vfx.VfxCompletenessTest' --no-daemon --max-workers=1 --no-watch-fs` — passed, including the new caster-action cue and 34-id recipe/radius contracts.
- `./gradlew.bat test --tests 'jujutsu.mod.client.render.CharacterSkinAnimationPackTest' --no-daemon --max-workers=1 --no-watch-fs` — passed for all three rigs, clip sets, loop flags and trigger paths.
- `./gradlew.bat compileClientJava --no-daemon --max-workers=1 --no-watch-fs` — passed after the controller/resource changes.
- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` — passed, including documentation audit, both source sets, JUnit, 31 verification JavaExec tasks and assertion checks.
- `./gradlew.bat assemble --no-daemon --max-workers=1 --no-watch-fs` — pending for final jar packaging.
- Interactive F5, idle/walk/run, vessel actions, held items, armor/cape visibility and first-person effects remain manual checks; no UI automation is used for this handoff.

## Next steps

1. Build/publish the branch and open the stacked PR.
2. Perform the remaining manual in-game visual smoke when a human-controlled client session is available.
