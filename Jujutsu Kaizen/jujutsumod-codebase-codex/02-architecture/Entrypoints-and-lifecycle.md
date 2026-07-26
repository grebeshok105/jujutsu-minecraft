# Entrypoints and Lifecycle

Status: CURRENT

## Common entrypoint

JujutsuMod.onInitialize registers entities, persistent attachments, data components, items, particles, sounds, effects, networking, CharacterAbilityCooldowns, CharacterCombatModifiers, TodoBlackFlashRuntime.register(), TodoBoogieWoogieRuntime.register(), ritual/runtime systems (ProjectJjkRitualRuntime, ProjectJjkStrawDollRuntime, NobaraHammerCombatRuntime, NobaraActionGuard, SelfResonanceRuntime, NailTrapRuntime), EmbeddedNailRegistry, NailAnchorLifecycle, commands, and debug Black Flash support.

`TodoBoogieWoogieRuntime.register()` exists only to attach an END_WORLD_TICK listener that drains the delayed clap-sound queue; the swap itself is invoked from `CharacterAbilityExecutor.tryCast`, not from a registered event (VERIFIED — TodoBoogieWoogieRuntime.register, .tickClapSounds).

Important lifecycle owners:

- Character state is stored on ServerPlayer through JujutsuAttachments.CHARACTER_STATE.
- CharacterSelectionManager.syncOnJoin exchanges current online selections; disconnect broadcasts None without deleting persistence.
- EmbeddedNailRegistry tracks loaded ordinary embedded nails and clears server-level maps on SERVER_STOPPING.
- Runtime systems register their own tick/disconnect/stop cleanup where required.

## Client entrypoint

JujutsuModClient registers the nail entity renderer and straw-doll item renderer, particle factories, VfxDirector, JujutsuVfxRecipes.registerAll() (Nobara + Todo), client payload receivers, keybinds, SDF/MSDF pipelines, and the ClickGui host.

The vessel GeckoLib renderers are **not** registered here. `CharacterGeoRenderers.create(context)` is called from `CharacterRenderDispatchMixin` inside the `LivingEntityRenderer` constructor, once per `PlayerRenderer`, because that is the only place with the `EntityRendererProvider.Context` the renderers need (VERIFIED). See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
