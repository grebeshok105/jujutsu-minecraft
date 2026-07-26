# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- CharacterAbility — shared active-technique slots; currently PRIMARY only, with a stable `networkId` used on the wire.
- CharacterAbilityExecutor.tryCast — roster-wide ability gate/router. Owns the not-selected and cooldown checks, then switches exhaustively on JujutsuCharacter. A new vessel must add an arm here.
- ProjectJjkNobaraActions.tryCast — explicit Nobara action gate/router.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- VfxCue and JujutsuNetworking broadcast/send helpers — server presentation contract.
- VfxDirector.register and VfxContext channels — client recipe contract.
- CharacterGeoRenderers.create — client-side vessel-to-renderer map. Exhaustive switch with no default, so it is the compile-time gate for a new vessel's rendering decision; absence from the returned map means vanilla player rendering.

Client render seams are documented in [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
