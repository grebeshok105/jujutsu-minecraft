# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- ProjectJjkNobaraActions.tryCast — explicit Nobara action gate/router.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- VfxCue and JujutsuNetworking broadcast/send helpers — server presentation contract.
- VfxDirector.register and VfxContext channels — client recipe contract.

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
