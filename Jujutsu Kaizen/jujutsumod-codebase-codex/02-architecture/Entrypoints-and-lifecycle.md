# Entrypoints and Lifecycle

Status: CURRENT

## Common entrypoint

JujutsuMod.onInitialize registers entities, persistent attachments, data components, items, particles, sounds, effects, networking, CharacterAbilityCooldowns, CharacterCombatModifiers, commands, and debug Black Flash support. It names no vessel: per-vessel runtimes install through each vessel's `CharacterDefinition.registerServerHooks()`, called in a loop over `JujutsuCharacters.all()` — Nobara's definition registers her eight runtimes (ProjectJjkRitualRuntime, ProjectJjkStrawDollRuntime, EmbeddedNailRegistry, NailAnchorLifecycle, NobaraHammerCombatRuntime, NobaraActionGuard, SelfResonanceRuntime, NailTrapRuntime) and Todo's registers his four (TodoBlackFlashRuntime, TodoBoogieWoogieRuntime, TodoPairSwapRuntime, TodoSwapMarks). See [Vessel definitions](Vessel-definitions.md).

`TodoBoogieWoogieRuntime.register()` exists only to attach an END_WORLD_TICK listener that drains the delayed clap-sound queue; the swap itself is invoked through `CharacterAbilityExecutor.tryCast` → `TodoDefinition.tryCast` → `TodoAbilityRouter`, not from a registered event (VERIFIED — TodoBoogieWoogieRuntime.register, .tickClapSounds).

Important lifecycle owners:

- Character state is stored on ServerPlayer through JujutsuAttachments.CHARACTER_STATE.
- CharacterSelectionManager.syncOnJoin exchanges current online selections; disconnect broadcasts None without deleting persistence.
- EmbeddedNailRegistry tracks loaded ordinary embedded nails and clears server-level maps on SERVER_STOPPING.
- Runtime systems register their own tick/disconnect/stop cleanup where required.

## Client entrypoint

JujutsuModClient first hands the client's selection mirror to `CharacterSelectionView.setClientLookup` — so shared code that runs on both sides, like an item's `use`, can ask which vessel a player is without `src/main` touching a client class — then registers particle factories, VfxDirector, then `JujutsuCharacterClients.registerAll()` — each vessel installs its own client hooks through `CharacterClientDefinition.registerClientHooks()`, which is where the nail entity renderer, the straw-doll item renderer, and the per-vessel VFX recipe packs now register (the aggregate `JujutsuVfxRecipes` is deleted). It must follow `VfxDirector.initialize()` because the recipes register into the director it builds. Then client payload receivers, keybinds, SDF/MSDF pipelines, and the ClickGui host. See [Vessel definitions](Vessel-definitions.md).

Player vessel animation is **not** registered here. `CharacterSkinAnimationMixin` asks the selected client definition for its `skinAnimation()` adapter during the existing vanilla player render, after `PlayerModel.setupAnim`; the adapter evaluates GeckoLib clips on an invisible rig and vanilla continues to draw the skin and layers. See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
