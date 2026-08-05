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
| VFX uses one cue/director/recipe path | VfxDirector, JujutsuClientNetworking, NobaraVfxRecipes | VERIFIED |
| Nobara defines 21 live VFX ids | NobaraVfxIds | VERIFIED |
| Client mixin count is 6 | jujutsumod.client.mixins.json | VERIFIED |
| Verification program inventory | build.gradle `verifyAssertionsEnabled` | VERIFIED |
| Roster panel has three cards (Nobara/Todo/None) | CharacterRosterPanel.CARDS, initialized from JujutsuCharacterClients.inRosterOrder | VERIFIED |
| Vessel skin animation choice sits behind a compile-time exhaustive switch | JujutsuCharacterClients.definition; CharacterSkinAnimationRenderer.apply asks each definition | VERIFIED |
| NONE means vanilla player rendering and pose | NoneClientDefinition inherits the null skinAnimation default; CharacterSkinAnimationMixin leaves missing adapters untouched | VERIFIED |
| Boogie Woogie commits only when both destinations are safe | TodoSwapPlan.preflight, TodoBoogieWoogieRuntime.tryCast | VERIFIED |
| Boogie Woogie has no floor check and no third-party occupancy gate | TodoBoogieWoogieRuntime.findSafeDestination, .isPlaceableDestination | VERIFIED |
| Boogie Woogie rollback is best-effort and logs an incomplete restore | TodoBoogieWoogieRuntime.tryCast, .restore | VERIFIED |
| Todo reuses Nobara's Black Flash cue id | TodoBlackFlashRuntime.afterDamage, NobaraVfxIds.BLACK_FLASH | VERIFIED, known seam |
| Only Todo can throw the swap marker, checked on both sides | TodoSwapMarkerItem.use, CharacterSelectionView.of | VERIFIED |
| GeckoLib 5 loads only geckolib/models and geckolib/animations | live skin rigs under `geckolib/models/character_skin`, existing animation JSON, archive manifest and ProjectSanityTest | VERIFIED |
| ClickGui registers one module per vessel, derived from the client registry (three today) | JujutsuModules.registerAll, JujutsuCharacterClients.all | VERIFIED |
| Boom detonates all loaded owned nails with no aim gate | ProjectJjkRitualRuntime.collectAllLoadedOwnedNails | VERIFIED |
| ProjectJJK assets are temporary permitted placeholders | user decision, legal import note | VERIFIED for private development |
| Rich provenance is release-ready | no durable permission/license found | UNKNOWN |
| In-game visual feel is correct | no current smoke evidence | UNKNOWN |
