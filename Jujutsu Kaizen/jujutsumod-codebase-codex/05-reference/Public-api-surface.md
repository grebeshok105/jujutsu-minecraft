# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- CharacterAbility — shared active-technique slots, named after input position: PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click holding a technique weapon). Each carries a stable `networkId` used on the wire; append new slots, never renumber.
- CharacterAbilityExecutor.tryCast — roster-wide ability gate/router. Owns the not-selected and cooldown checks, then switches exhaustively on JujutsuCharacter. A new vessel must add an arm here.
- NobaraAbilityRouter.tryCast / TodoAbilityRouter.tryCast — per-vessel slot maps, each an exhaustive CharacterAbility switch with no default. Nobara's fills all five slots and also owns her stagger check and her single fallback message; Todo's answers false on the two slots he does not use.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- VfxCue and JujutsuNetworking broadcast/send helpers — server presentation contract.
- VfxDirector.register and VfxContext channels — client recipe contract.
- CharacterGeoRenderers.create — client-side vessel-to-renderer map. Exhaustive switch with no default, so it is the compile-time gate for a new vessel's rendering decision; absence from the returned map means vanilla player rendering.

Client render seams are documented in [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
