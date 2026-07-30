# Claim-to-Source Index

Status: CURRENT

| Claim | Source | State |
|---|---|---|
| Fabric 1.21.8 / Java 21 | gradle.properties, build.gradle, fabric.mod.json | VERIFIED |
| N opens the only product menu | JujutsuKeybinds.register, ClickGui | VERIFIED |
| Characters panel applies a server selection | CharacterRosterPanel.applySelection, SelectCharacterPayload | VERIFIED |
| Selection persists; the starter claim is recorded for every vessel and read by nothing | CharacterPlayerState, JujutsuAttachments, CharacterSelectionManager.select | VERIFIED |
| Nobara's starter kit is restored idempotently on every selection | NobaraDefinition.onSelected, ProjectJjkNobaraLoadout.ensureStarterTools | VERIFIED |
| Every vessel binds one server and one client definition through two exhaustive switches | JujutsuCharacters.definition, JujutsuCharacterClients.definition | VERIFIED |
| C2S actions execute on server thread | JujutsuNetworking.registerServerReceivers | VERIFIED |
| Nobara actions require selected Nobara | CharacterAbilityExecutor.tryCast resolves the stored selection; JujutsuNetworking.handleCharacterAbility refuses a mismatched vessel claim | VERIFIED |
| Hairpin uses concrete loaded owner nails | EmbeddedNailRegistry.loadedOwnedNails, ProjectJjkRitualRuntime | VERIFIED |
| Embedded nail TTL/cap are 1200/30 | ProjectJjkNobaraProfile | VERIFIED |
| Resonance changes global server TPS | ProjectJjkStrawDollRuntime.resolveImpact, ServerTimeDilation | VERIFIED and accepted |
| VFX uses one eight-field cue/director/recipe path | VfxCuePayload, VfxDirector, the three vessel recipe packs | VERIFIED |
| Nobara defines 21 live VFX ids | NobaraVfxIds | VERIFIED |
| Todo defines 7 live VFX ids | TodoVfxIds.LIVE | VERIFIED |
| Megumi defines 5 live VFX ids | MegumiVfxIds.LIVE | VERIFIED |
| All VFX PLANNED sets are empty | NobaraVfxIds.PLANNED, TodoVfxIds.PLANNED, MegumiVfxIds.PLANNED | VERIFIED |
| VFX Core has seven live director channels | VfxDirector | VERIFIED |
| VFX Core has three recipe packs | CharacterClientDefinition.registerClientHooks | VERIFIED |
| World rendering has five family files and a retained cap of 48 | VfxWorldChannel, client/vfx/world | VERIFIED |
| Client mixin count is 6 | jujutsumod.client.mixins.json | VERIFIED |
| Verification program inventory | build.gradle `verifyAssertionsEnabled` | VERIFIED |
| Roster panel has four live cards (Nobara/Todo/Megumi/None) | CharacterRosterPanel.CARDS, initialized from JujutsuCharacterClients.inRosterOrder | VERIFIED |
| Vessel renderer choice sits behind a compile-time exhaustive switch | JujutsuCharacterClients.definition; CharacterGeoRenderers.create asks each definition | VERIFIED |
| NONE means vanilla player rendering | NoneClientDefinition inherits the null createRenderer default, CharacterRenderDispatchMixin | VERIFIED |
| Boogie Woogie commits only when both destinations are safe | TodoSwapPlan.preflight, TodoBoogieWoogieRuntime.tryCast | VERIFIED |
| Boogie Woogie has no floor check and no third-party occupancy gate | TodoBoogieWoogieRuntime.findSafeDestination, .isPlaceableDestination | VERIFIED |
| Boogie Woogie rollback is best-effort and logs an incomplete restore | TodoBoogieWoogieRuntime.tryCast, .restore | VERIFIED |
| Todo reuses Nobara's Black Flash cue id | TodoBlackFlashRuntime.afterDamage, NobaraVfxIds.BLACK_FLASH | VERIFIED, known seam |
| Only Todo can throw the swap marker, checked on both sides | TodoSwapMarkerItem.use, CharacterSelectionView.of | VERIFIED |
| GeckoLib 5 loads only geckolib/models and geckolib/animations | live asset layout; ProjectSanityTest negative assertion on `geo/projectjjk` | VERIFIED |
| ClickGui registers one module per vessel, derived from the client registry (four including None today) | JujutsuModules.registerAll, JujutsuCharacterClients.all | VERIFIED |
| Boom detonates all loaded owned nails with no aim gate | ProjectJjkRitualRuntime.collectAllLoadedOwnedNails | VERIFIED |
| ProjectJJK assets are temporary permitted placeholders | user decision, legal import note | VERIFIED for private development |
| Rich provenance is release-ready | no durable permission/license found | UNKNOWN |
| In-game visual feel is correct | no current smoke evidence | UNKNOWN |
