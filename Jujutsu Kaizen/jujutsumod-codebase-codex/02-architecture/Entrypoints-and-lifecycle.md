# Entrypoints and Lifecycle

Status: CURRENT

## Common entrypoint

JujutsuMod.onInitialize registers entities, persistent attachments, data components, items, particles, sounds, effects, networking, ritual/runtime systems, EmbeddedNailRegistry, NailAnchorLifecycle, commands, and debug Black Flash support.

Important lifecycle owners:

- Character state is stored on ServerPlayer through JujutsuAttachments.CHARACTER_STATE.
- CharacterSelectionManager.syncOnJoin exchanges current online selections; disconnect broadcasts None without deleting persistence.
- EmbeddedNailRegistry tracks loaded ordinary embedded nails and clears server-level maps on SERVER_STOPPING.
- Runtime systems register their own tick/disconnect/stop cleanup where required.

## Client entrypoint

JujutsuModClient registers entity/GeckoLib renderers, particle factories, VfxDirector, Nobara recipes, client payload receivers, keybinds, SDF/MSDF pipelines, and the ClickGui host.
