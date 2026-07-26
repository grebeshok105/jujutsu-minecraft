# Vessel Render Stack

Status: CURRENT

How a selected vessel replaces the vanilla player model. Everything below is VERIFIED against `src/client/java/jujutsu/mod/client/render/**` and `src/client/java/jujutsu/mod/client/mixin/**` unless labelled otherwise.

## Dispatch

`CharacterRenderDispatchMixin` targets `LivingEntityRenderer` and is the single dispatch point for the whole roster — it is deliberately not Nobara-specific.

1. In the `LivingEntityRenderer` constructor, and only when `this instanceof PlayerRenderer`, it builds the vessel map via `CharacterGeoRenderers.create(context)`, which asks each vessel's `CharacterClientDefinition.createRenderer`. This is the only place that has the `EntityRendererProvider.Context` the renderers need, which is why the map is not built in `JujutsuModClient`.
2. At `render(LivingEntityRenderState, …)` HEAD it bails out unless the state is a `PlayerRenderState` and the player is not a spectator.
3. It resolves the selection with `ClientCharacterSelectionManager.selectionByEntityId`, looks up the renderer, and bails if either is missing.
4. It needs a live `AbstractClientPlayer` plus a partial tick, which vanilla's render state does not carry. `PlayerRenderContextMixin` records that pair, and the dispatch reads it back through `ClientCharacterSelectionManager.renderContextByEntityId`. No context means no vessel render.
5. Only if `renderer.renderCharacter(...)` returns `true` does it `ci.cancel()`.

## The cancel contract

`CharacterGeoRenderer.renderCharacter` returns `true` when it drew the player and the vanilla path must be cancelled. That boolean is the whole contract: a renderer that cannot draw returns `false` and vanilla continues. There is no silent-catch fallback to the vanilla skin — that was a real historical bug (a swallowed `IllegalArgumentException` from missing GeckoLib data tickets rendered the old skin instead of failing), and `ProjectSanityTest` now asserts the catch is gone.

`CharacterPlayerGeoRenderer.renderCharacter` is `final`. Subclasses must not override it.

## Vessel map

`CharacterGeoRenderers.create` fills an `EnumMap<JujutsuCharacter, CharacterGeoRenderer>` by asking every client definition in `JujutsuCharacterClients.all()` for its renderer:

| Vessel | `createRenderer` answers |
|---|---|
| NOBARA | `NobaraPlayerGeoRenderer` (from `NobaraClientDefinition`) |
| TODO | `TodoPlayerGeoRenderer` (from `TodoClientDefinition`) |
| NONE | `null` — omitted from the map, meaning vanilla player rendering |

The switch this file used to hold moved into `JujutsuCharacterClients.definition`, which is **exhaustive with no `default`**: a new `JujutsuCharacter` constant fails compilation there until it is bound to a client definition, and that definition either declares a renderer or inherits the `null` default. `null` is the explicit vanilla opt-in, not an oversight. See [Vessel definitions](../02-architecture/Vessel-definitions.md).

## Shared renderer: pose-stack guard

`CharacterPlayerGeoRenderer` extends GeckoLib's `GeoReplacedEntityRenderer` and implements `CharacterGeoRenderer`. Subclasses declare only model, animatable, render layers, and scale — `NobaraPlayerGeoRenderer` and `TodoPlayerGeoRenderer` are each under 20 lines (Nobara scale 0.94, Todo 0.96).

`renderCharacter` pushes a pose, captures `matrices.last()` as a guard token, and in a `finally` block pops until `matrices.last()` is that token again, then pops the token itself. This exists because an unbalanced vessel render corrupts the shared stack for the rest of the frame and crashes elsewhere with `IllegalStateException: Pose stack not empty` — a crash the project has actually hit. The guard turns a bad frame into a bad-looking frame instead of a client crash.

## The HUMANOID_MODEL bridge

GeckoLib bones know nothing about vanilla arm poses, so the shared stack bridges vanilla's own pose calculation across:

- **Write** — `CharacterPlayerGeoRenderer.addRenderData` runs `vanillaPoseModel.setupAnim(renderState)` on a private `PlayerModel` baked from `ModelLayers.PLAYER`, then publishes it as `renderState.addGeckolibData(DataTickets.HUMANOID_MODEL, vanillaPoseModel)`.
- **Read** — `CharacterPlayerGeoModel.applyVanillaArmPose` pulls it back with `getOrDefaultGeckolibData(DataTickets.HUMANOID_MODEL, null)` and copies `rightArm` / `leftArm` rotations onto the vessel's `rightArm` / `leftArm` bones, negating X and Y (`setRotX(-vanillaArm.xRot)`, `setRotY(-vanillaArm.yRot)`, `setRotZ(vanillaArm.zRot)`) and zeroing `right_elbow` / `left_elbow`.

It short-circuits: if a keyframed action clip owns the arms, or the player is neither using an item nor holding a non-EMPTY `ArmPose` on either side, the copy is skipped entirely and the animation keeps the arms. A null ticket is tolerated, not fatal.

Every bone write calls `resetStateChanges()` so these render-only corrections stay out of GeckoLib's next-frame reset bookkeeping.

## Head-look clamps

`CharacterPlayerGeoModel` clamps look angles to `MAX_HEAD_YAW_DEGREES = 38.0f` and `MAX_HEAD_PITCH_DEGREES = 22.0f`. The in-source rationale is concrete: an earlier 75/45 attempt tore the head off the neck seam. Yaw is `Mth.wrapDegrees(yRot - bodyRot)` clamped, pitch is `xRot` clamped.

The look is applied as an **offset from the animated rest pose** (`head.setRotY(head.getRotY() - …)`), not as an absolute rotation, so idle/walk head keyframes cannot pin yaw at zero across frames. Vessels differ only in `headLookWeight` — how strongly an action clip damps the look — and the whole block is skipped when weight is at or below 0.01.

## Held items

`CharacterHeldItemLayer` extends `BlockAndItemGeoLayer`. Subclasses supply only two bone names. Two shared `DataTicket<ItemStack>` instances (`character_right_hand_item`, `character_left_hand_item`) carry the stacks, deliberately shared rather than per-character: a render state belongs to exactly one player drawn by exactly one vessel renderer, so per-character ticket ids would never disambiguate anything.

`addRenderData` resolves main/off hand against `player.getMainArm()` so left-handed players get the right stack on the right bone. `renderStackForBone` applies the vanilla-equivalent ±90° X rotation and a small translate, with extra offsets and a 180° Y flip for shields.

## First-person: two styles, one mixin

`FirstPersonHandFxMixin` owns both first-person hand treatments, driven by `VfxDirector.firstPersonStyle()`. There is deliberately no per-vessel first-person mixin.

| Style | Vessel | Behaviour |
|---|---|---|
| SNAP | Nobara (`NobaraVfxRecipes` → `firstPerson().triggerSnap`) | Whole-stack transform pushed at `renderHandsWithItems` HEAD and popped at RETURN. The vanilla hand path continues normally underneath. |
| CLAP | Todo (`TodoVfxRecipes` → `firstPerson().triggerClap`) | Cancels `renderHandsWithItems` outright and draws **both** arms itself. |

SNAP is the channel's default state. CLAP exists because vanilla's empty-hand path only ever draws the main arm, so an off-hand clap would be invisible; the mixin calls `renderPlayerArm` for RIGHT then LEFT in fixed order — independent of the main-hand setting — so both arms share a base pose. Two `@ModifyVariable` hooks force `equippedProgress` and `swingProgress` to 0 while clapping to kill attack/item residual, and the side-mirrored meet offset comes from `VfxDirector.firstPersonClapArmPose(arm, progress)`.

Three rules make CLAP correct, each of which was a live bug first, and `ProjectSanityTest` now guards all three:

1. **Flush before cancelling.** Cancelling `renderHandsWithItems` skips vanilla's terminal `endBatch()`. Arms are only submitted to the GPU there, and `RenderType.draw` samples the model-view at draw time, so without the flush the draw is deferred to `GameRenderer`'s later flush — after `popMatrix()` has reset the model-view — and the arms pick up a rigid rotation by the camera's orientation about the camera origin. Symptom: hand position depends on where the player was looking, and an arm can end up behind the near plane.
2. **Each arm owns a matched push/pop.** `ItemInHandRenderer.renderPlayerArm` mutates the caller's pose stack and never restores it; vanilla compensates by wrapping every call. An aggregate depth counter is not enough, because a skipped push and a taken pop pair only in total and will pop another frame's transform.
3. **One progress sample per frame.** The channel's clock is wall-time, so sampling per arm puts the two hands on different instants — and if the animation expires between them, the second arm renders unposed at the vanilla rest pivot. `progress()` is a pure read; expiry is the explicit once-per-frame `expireIfFinished()`. `style()` expires too, so a clap played entirely in third person cannot keep cancelling the vanilla hand path.

`cancel()` drops an in-flight animation. `ClientCharacterSelectionManager` calls it when the local player's vessel changes, because otherwise the mixin keeps cancelling the vanilla hand path for the rest of the run and held items disappear. `triggerSnap` refuses to clobber an in-flight CLAP: one channel serves every vessel, and reinterpreting a clap with snap timing was a cross-vessel state leak.

Known and unfixed: the arms cross over at contact. The meet yaw is applied about the camera origin rather than the shoulder, so on a pivot ~0.82 blocks out it contributes roughly twice the intended inward travel. INFERRED from geometry, not yet confirmed on screen.

Clap offsets stay small on purpose: parent rotations multiply `renderPlayerArm`'s large fixed translates, so a value that looks reasonable in isolation throws the arm off-screen.

## Two traps worth keeping written down

**`speedValue` is not a movement flag.** Vanilla's `HumanoidRenderState.speedValue` is a limb-animation divisor/scale, not a speed. Using it as a `> 0.82f` movement test makes the run clip play while standing still. The animatables now decide movement from GeckoLib's own data plus real state: `state.isMoving()`, horizontal velocity from `DataTickets.VELOCITY`, and `DataTickets.SPRINTING`, with `walkSpeed` only as one contributing term (VERIFIED — NobaraPlayerGeoAnimatable).

**Root bones must follow the body.** The ProjectJJK source model has `bb_main` — which holds the coat/skirt panels — as a *root* bone while `skirt` is parented to `body`. On a player-replacement render that leaves the clothing floating in world space instead of following the torso. The runtime copy re-parents `bb_main` to `skirt`, so `bb_main → skirt → body` (VERIFIED — assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json; `ProjectSanityTest` asserts the parenting). Any re-import from the upstream asset will reintroduce the float — check the hierarchy, not just the geometry.

## What still needs a real client

Scale, offsets, clap pose, and head-look feel are UNKNOWN from a headless review. Compilation proves the wiring only — run `runClient` and check third person (F5), a held item, a shield, and the clap.
