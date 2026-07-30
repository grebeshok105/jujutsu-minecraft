# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- CharacterAbility — shared active-technique slots, named after input position: PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click holding a technique weapon), and USE_CONTEXT (the completed two-right-click input). Each carries a stable `networkId` used on the wire; append new slots, never renumber.
- CharacterDefinition + JujutsuCharacters — the server half of the vessel seam: `id`, `tryCast`, and defaulted hooks (`registerServerHooks`, `canonicalSlot`, `applyAttributes`/`removeAttributes`, `adjustIncomingStaggerTicks`, `onSelected`/`onDeselected`). The registry's exhaustive switch is the server-side compile-time gate for a new vessel. See [Vessel definitions](../02-architecture/Vessel-definitions.md).
- CharacterClientDefinition + JujutsuCharacterClients — the client half: `id`, `rosterEntry`, and defaulted hooks (`createRenderer`, `playerSkin`, `rosterOrder`, `accent`/`warmth`, `registerClientHooks`, module row). Same exhaustive-switch guarantee on the client side.
- CharacterAbilityExecutor.tryCast — roster-wide ability gate. Owns the not-selected and cooldown checks, folding the slot through the vessel's `canonicalSlot` **before** the cooldown check so two inputs a vessel treats as one share a cooldown, then asks the selected vessel's CharacterDefinition. It names no vessel; a new vessel never edits it.
- NobaraAbilityRouter.tryCast / TodoAbilityRouter.tryCast / MegumiAbilityRouter.tryCast — per-vessel slot maps, each an exhaustive CharacterAbility switch with no default. Unused slots answer false explicitly.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- VfxCue, VfxCuePayload, VfxCues and JujutsuNetworking broadcast/send helpers — the eight-field server presentation contract. `VfxCues` owns common transport shapes; it does not create effect-specific APIs.
- VfxDirector.register and VfxContext channels — client recipe contract. The seven channels and existing world-style boundary are frozen seams.
- TodoSwapArrivalPayload.from — named read model for the overloaded `SWAP_ARRIVAL` offset; it does not change the wire format.
- CharacterGeoRenderers.create — client-side vessel-to-renderer map, filled by asking each client definition's `createRenderer`; the compile-time exhaustiveness lives in JujutsuCharacterClients now. Absence from the returned map (a `null` renderer) means vanilla player rendering.

Client render seams are documented in [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
