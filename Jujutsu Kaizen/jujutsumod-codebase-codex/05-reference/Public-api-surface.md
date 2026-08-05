# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- CharacterAbility — shared active-technique slots, named after input position: PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click holding a technique weapon). Each carries a stable `networkId` used on the wire; append new slots, never renumber.
- CharacterDefinition + JujutsuCharacters — the server half of the vessel seam: `id`, `tryCast`, and defaulted hooks (`registerServerHooks`, `canonicalSlot`, `applyAttributes`/`removeAttributes`, `adjustIncomingStaggerTicks`, `onSelected`/`onDeselected`). The registry's exhaustive switch is the server-side compile-time gate for a new vessel. See [Vessel definitions](../02-architecture/Vessel-definitions.md).
- CharacterClientDefinition + JujutsuCharacterClients — the client half: `id`, `rosterEntry`, and defaulted hooks (`skinAnimation`, `playerSkin`, `rosterOrder`, `accent`/`warmth`, `registerClientHooks`, module row). Same exhaustive-switch guarantee on the client side.
- CharacterAbilityExecutor.tryCast — roster-wide ability gate. Owns the not-selected and cooldown checks, folding the slot through the vessel's `canonicalSlot` **before** the cooldown check so two inputs a vessel treats as one share a cooldown, then asks the selected vessel's CharacterDefinition. It names no vessel; a new vessel never edits it.
- NobaraAbilityRouter.tryCast / TodoAbilityRouter.tryCast — per-vessel slot maps, each an exhaustive CharacterAbility switch with no default. Nobara's fills all five slots and also owns her stagger check and her single fallback message; Todo's answers false on the two slots he does not use.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- VfxCue and JujutsuNetworking broadcast/send helpers — server presentation contract.
- VfxDirector.register and VfxContext channels — client recipe contract.
- CharacterSkinAnimationRenderer.apply — shared selection-to-adapter dispatch; it asks the client definition for `skinAnimation()` and applies the result to the vanilla `PlayerModel`. A `null` adapter means ordinary vanilla pose. The compile-time exhaustiveness lives in `JujutsuCharacterClients`.

Client render seams are documented in [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
