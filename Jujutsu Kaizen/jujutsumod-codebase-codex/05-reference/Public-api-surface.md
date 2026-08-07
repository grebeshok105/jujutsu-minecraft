# Public API Surface for Future Changes

Status: CURRENT

Stable internal integration points:

- JujutsuMod.id — namespaced ids.
- JujutsuCharacter — current vessel ids/model ids.
- CharacterSelectionManager.select/selected/syncOnJoin — server selection lifecycle.
- CharacterPlayerState and JujutsuAttachments.CHARACTER_STATE — persistent player state.
- CharacterAbility — shared active-technique slots, named after input position: PRIMARY (R), PRIMARY_SNEAK (Shift+R), SECONDARY (B), SECONDARY_SNEAK (Shift+B), ATTACK_CONTEXT (left click holding a technique weapon), USE_CONTEXT (a completed right-click pair; reserved — no vessel answers it), SECONDARY_SNEAK_HOLD/SECONDARY_SNEAK_RELEASE (the held-gesture wire pair), TERTIARY (V), TERTIARY_SNEAK (Shift+V). Each carries a stable `networkId` used on the wire; append new slots, never renumber.
- CharacterDefinition + JujutsuCharacters — the server half of the vessel seam: `id`, `tryCast`, and defaulted hooks (`registerServerHooks`, `applyAttributes`/`removeAttributes`, `adjustIncomingStaggerTicks`, `onSelected`/`onDeselected`, `selectCurseLink`). The registry's exhaustive switch is the server-side compile-time gate for a new vessel. See [Vessel definitions](../02-architecture/Vessel-definitions.md).
- CharacterClientDefinition + JujutsuCharacterClients — the client half: `id`, `rosterEntry`, and defaulted hooks (`skinAnimation`, `playerSkin`, `rosterOrder`, `accent`/`warmth`, `registerClientHooks`, module row). Same exhaustive-switch guarantee on the client side.
- CharacterAbilityExecutor.tryCast — roster-wide ability gate. Owns the not-selected and cooldown checks, then asks the selected vessel's CharacterDefinition; every slot reaches the vessel as itself — the `canonicalSlot` fold is deleted with its only implementer (the Todo stone rework). It names no vessel; a new vessel never edits it.
- NobaraAbilityRouter / TodoAbilityRouter / MegumiAbilityRouter `.tryCast` — per-vessel slot maps, each an exhaustive CharacterAbility switch with no default. Nobara's fills five slots and also owns her stagger check and her single fallback message; Todo's fills six (R, Shift+R, B, Shift+B, V, Shift+V); Megumi's covers the summon pair, the trap, the move's hold/release and the drop.
- ProjectJjkNobaraProfile — centralized gameplay constants.
- EmbeddedNailRegistry.loadedOwnedNails — bounded loaded owner-nail query.
- Dev-control wipe/query accessors — small public statics added for the MCP dev-control surface and gametests (never called by gameplay): `CharacterAbilityCooldowns.clearAllForPlayer`, `CombatStagger.clear(UUID)`, `BlackFlashFocus.clear`, `EmbeddedNailRegistry.discardOwned`, `NailTrapRuntime.clearOwned`, `ProjectJjkNailMarks.clearAll`, `ProjectJjkStrawDollRuntime.resetCaster`, `SelfResonanceRuntime.clearCaster`, `NobaraHammerCombatRuntime.clearPlayer`, `ProjectJjkNobaraRuntime.clearPlayer`, `ProjectJjkRitualRuntime.restoreAllGlow`, `MegumiSummonRuntime.packView` + `TeardownReason.FIXTURE_RESET` (no cooldown), `MegumiShadowTrapRuntime`/`MegumiShadowDropRuntime` `hasOwned`/`clearOwned`, `MegumiShadowMoveRuntime.hasOwned`/`teardownOwned`. The full contract table lives in [docs/MCP_DEV_CONTROLS.md](../../../docs/MCP_DEV_CONTROLS.md).
- VfxCue and JujutsuNetworking broadcast/send helpers — server presentation contract.
- VfxDirector.register and VfxContext channels — client recipe contract.
- CharacterSkinAnimationRenderer.apply — shared selection-to-adapter dispatch; it asks the client definition for `skinAnimation()` and applies the result to the vanilla `PlayerModel`. A `null` adapter means ordinary vanilla pose. The compile-time exhaustiveness lives in `JujutsuCharacterClients`.

Client render seams are documented in [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).

These are project APIs, not promised third-party compatibility. Change them with tests and Codex updates. Avoid exposing mutable global collections.
