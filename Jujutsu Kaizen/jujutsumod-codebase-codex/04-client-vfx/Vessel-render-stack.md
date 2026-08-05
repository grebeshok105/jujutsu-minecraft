# Vessel Render Stack

Status: CURRENT

The selected Nobara, Todo, or Megumi vessel keeps its ordinary Minecraft player skin and vanilla
`PlayerRenderer` geometry. GeckoLib remains the third-person animation runtime: each newly authored
vessel animation pack is evaluated on a small invisible humanoid rig, then the supported bone
transforms are copied to the live vanilla `PlayerModel` for that frame.

## Dispatch

`CharacterSkinAnimationMixin` is the shared render hook. It does not replace or cancel vanilla player
rendering.

1. `PlayerRenderContextMixin` records the live `AbstractClientPlayer` and partial tick while
   `PlayerRenderer` extracts its `PlayerRenderState`.
2. After `EntityModel.setupAnim` has prepared the vanilla model, the skin hook resolves the selection
   through `ClientCharacterSelectionManager` and asks `CharacterSkinAnimationRenderer` for the selected
   definition's `skinAnimation()` adapter.
3. The adapter evaluates GeckoLib and applies its pose to the already prepared `PlayerModel`.
4. The wrapped vanilla render continues normally, so standard skin layers, armor, capes, elytra,
   held-item layers, nameplates, invisibility and spectator handling remain owned by Minecraft.
5. A `finally` block closes `CharacterSkinAnimationState`, restoring every model part to the exact
   state captured before adaptation. Missing selection, context, or adapter leaves vanilla untouched.

The old canceling `CharacterRenderDispatchMixin` and visible replaced-player renderer stack are
archived under `archive/character-player-gecko/`. They are not in the client mixin list or runtime
source tree.

## Shared bridge

`CharacterSkinAnimationAdapter` implements GeckoLib's `GeoRenderer` contract only as an evaluation
surface. Its render type is `null`, its render callbacks are no-ops, and it never emits Geo geometry.
`fillRenderState` supplies the existing animatable instance and controller state; `GeoModel` prepares
the invisible rig and `handleAnimations` runs the current clips. GeckoLib normally augments
`PlayerRenderState` with `GeoRenderState` through its client mixin; the adapter checks that boundary and
returns `null` after closing its snapshot when the augmentation is absent, leaving vanilla rendering
untouched. Before `fillRenderState` evaluates the controllers, the adapter adds the live player's
`VELOCITY` and `SPRINTING` tickets and the vessel-specific animation data to the shared state.

`CharacterSkinAnimationModel` retains the former shared presentation rules:

- vanilla arm poses are copied through `DataTickets.HUMANOID_MODEL` when no keyframed action owns the
  arms;
- head look is applied as an offset from the animated head pose;
- yaw and pitch remain clamped to 38 and 22 degrees;
- vessel models supply only the animation resource, look weight and action guard.

The bridge maps `root`, `body`, `head`, both arms and both legs. `elbow`, `hand` and `knee` transforms
are folded into their parent limb because vanilla player geometry has no separate visible part for
them. Facial, hair and other Blockbench-only bones are evaluation-only and never create geometry.

`CharacterSkinAnimationState` snapshots position, rotation, scale, visibility and `skipDraw` for the
seven `PlayerModel` parts the bridge changes: root, body, head, both arms and both legs. It is
idempotently closed and restores those values even when GeckoLib evaluation or vanilla rendering throws.

The adapter converts each GeoBone's local position and rotation into vanilla coordinates, composes
parent-to-child transforms with quaternions, and extracts vanilla's `ZYX` Euler representation only
when writing a `ModelPart`. It does not add Euler angles component by component. Current skin rigs use
zero pivots and the bridge deliberately folds elbow, hand and knee bones into their visible parent
parts; non-humanoid bones remain evaluation-only.

The bridge is fail-loud after the GeckoLib state guard: a missing or malformed rig/animation throws
after the snapshot is restored instead of silently falling back to an unanimated selected vessel. This
keeps resource regressions visible while preserving vanilla state when the GeckoLib integration itself
is not present. The animation instance id uses the rendered player's UUID least-significant bits so
the singleton animatable's controller cache follows a player across entity-id changes such as respawn.

## Vessel bindings

Each client definition owns one adapter binding. The shared renderer asks the definition and never
switches on a vessel name.

| Vessel | Skin | Live GeckoLib animation source | Adapter/model |
|---|---|---|---|
| NOBARA | `textures/entity/character/nobara.png` | `geckolib/animations/projectjjk/npc.animation.json` | `NobaraSkinAnimationModel` |
| TODO | `textures/entity/character/todo.png` | `geckolib/animations/todo/todo_aoi.animation.json` | `TodoSkinAnimationModel` |
| MEGUMI | `textures/entity/character/megumi.png` | `geckolib/animations/megumi/megumi_fushiguro.animation.json` | `MegumiSkinAnimationAdapter` |
| NONE | player's own skin | none | `null`, ordinary vanilla pose |

The live third-person clip contract is intentionally explicit. Nobara's pack contains alternate idle
and walk loops, a dedicated run, `one_two`, `attack1`/`attack2`/`attack3`, `snap`, `spell1` through
`spell5`, `swipe1`, three hammer actions, the embedded-hammer and doll-strike actions,
`self_resonance`, and `black_flash`. Todo's pack contains alternate idle and walk loops, a dedicated
run, the externally triggerable `attack`, and the full `ability.boogie_woogie` clap; that clap keys
torso coil, forward dip and head recoil as well as the arms, without taking control of the lower body.
Megumi's pack contains idle, walk, run, `combat_idle`, `punch_1`, `punch_2`, `kick`, and the confirmed
`summon_divine_dogs` action. Its short post-hit `combat_idle` keeps only a restrained standing guard
and is selected only while the player is stationary, so walk/run remains authoritative. The
server-confirmed `nobara/caster_action` cue supplies the caster anchor for Nobara abilities whose main
world cue is target-fixed; it does not change gameplay timing.

Skin model IDs follow the vanilla layout: Nobara uses `slim`, while Todo and Megumi use `wide`
(`classic` in Minecraft skin terminology). Todo's 64x64 skin supplies the full classic four-pixel UV
coverage for both arm bases; executable coverage prevents a slim-layout texture from being paired with
wide geometry again. Todo's `1.15` body scale is applied by the common dimensions hook and the client
third-person render scale. It does not alter reach, damage or speed; the first-person path is unchanged.

Megumi's per-player `punch_1 -> punch_2 -> kick` bookkeeping lives in
`MegumiSkinAnimationAdapter`, not in a retired visible renderer. His skin model intentionally returns
zero procedural head-look weight, preserving the earlier approved "head facing forward" presentation;
the unused head-look helper was removed from `MegumiPlayerGeoAnimatable`. The confirmed summon cue
still triggers `summon_divine_dogs` through the existing VFX recipe and animatable.

## Runtime assets and archive

`geckolib/models/character_skin/{nobara,todo,megumi}.geo.json` contains bones only: no `cubes` and no
`uv` geometry. These rigs exist solely to give the existing animation processor the bone names it
expects. The ordinary 64x64 skin PNGs are the only visible player body textures.

The former visible Geo Java classes, Geo models, dedicated model textures and obsolete dispatch mixin
are retained at `archive/character-player-gecko/`. The superseded skin rigs and animation packs are
retained separately at `archive/character-skin-animation/`; `manifest.txt` in the Geo archive records
the original visible-model paths. Both archives are outside `src/main`, `src/client` and Gradle
resource processing, so they are not packaged into the mod jar. Only the new bone-only rigs and
animation packs under `src/main/resources/assets/jujutsumod/geckolib/` are live.

## First-person and persistent layers

The third-person bridge does not replace the existing first-person path. `FirstPersonHandFxMixin` still
owns the shared SNAP and CLAP treatments through VFX Core, and `CharacterSkinMixin` still maps a
selected vessel's ordinary skin to first-person hands. Vanilla's player layers continue to render
held items, armor, capes and elytra in third person.

Megumi's Divine Dog renderer remains a dedicated vanilla `WolfRenderer` seam and is unrelated to the
player skin bridge. Straw Doll, nail and swap-marker renderers remain registered by their vessel
definitions.

## Verification boundary

Focused source/resource checks, `CharacterSkinAnimationPackTest`, the bridge behavior tests and
`compileClientJava` prove the adapter contract, transform/state math, resource paths, clip/trigger
coverage and archive boundary. The full `qualityGate` is the automated completion gate. A real client
smoke is still required for F5 skin rendering, idle/walk/run, each vessel action, held items,
armor/cape visibility and first-person effects; a green gate does not prove those in-world visuals.
