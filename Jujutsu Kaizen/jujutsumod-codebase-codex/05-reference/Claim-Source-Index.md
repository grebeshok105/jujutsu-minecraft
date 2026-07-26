# Claim-to-Source Index

Status: CURRENT

| Claim | Source | State |
|---|---|---|
| Fabric 1.21.8 / Java 21 | gradle.properties, build.gradle, fabric.mod.json | VERIFIED |
| N opens the only product menu | JujutsuKeybinds.register, ClickGui | VERIFIED |
| Characters panel applies a server selection | CharacterRosterPanel.applySelection, SelectCharacterPayload | VERIFIED |
| Selection persists and starter claim is one-time | CharacterPlayerState, JujutsuAttachments, CharacterSelectionManager.select | VERIFIED |
| C2S actions execute on server thread | JujutsuNetworking.registerServerReceivers | VERIFIED |
| Nobara actions require selected Nobara | CharacterAbilityExecutor.tryCast, NobaraAbilityRouter.tryCast | VERIFIED |
| Hairpin uses concrete loaded owner nails | EmbeddedNailRegistry.loadedOwnedNails, ProjectJjkRitualRuntime | VERIFIED |
| Embedded nail TTL/cap are 1200/30 | ProjectJjkNobaraProfile | VERIFIED |
| Resonance changes global server TPS | ProjectJjkStrawDollRuntime.resolveImpact, ServerTimeDilation | VERIFIED and accepted |
| VFX uses one cue/director/recipe path | VfxDirector, JujutsuClientNetworking, NobaraVfxRecipes | VERIFIED |
| Nobara defines 25 VFX ids | NobaraVfxIds | VERIFIED |
| Client mixin count is 6 | jujutsumod.client.mixins.json | VERIFIED |
| Verification programs count is 28 | build.gradle `tasks.register('test…', JavaExec)` | VERIFIED |
| Roster panel has three cards (Nobara/Todo/None) | CharacterRosterPanel.CARDS | VERIFIED |
| Vessel renderer choice is a compile-time exhaustive switch | CharacterGeoRenderers.create | VERIFIED |
| NONE means vanilla player rendering | CharacterGeoRenderers.create, CharacterRenderDispatchMixin | VERIFIED |
| Boogie Woogie commits only when both destinations are safe | TodoSwapPlan.preflight, TodoBoogieWoogieRuntime.tryCast | VERIFIED |
| Boogie Woogie has no floor check and no third-party occupancy gate | TodoBoogieWoogieRuntime.findSafeDestination, .isPlaceableDestination | VERIFIED |
| Boogie Woogie rollback is best-effort and logs an incomplete restore | TodoBoogieWoogieRuntime.tryCast, .restore | VERIFIED |
| Todo reuses Nobara's Black Flash cue id | TodoBlackFlashRuntime.afterDamage, NobaraVfxIds.BLACK_FLASH | VERIFIED, known seam |
| GeckoLib 5 loads only geckolib/models and geckolib/animations | live asset layout; ProjectSanityTest negative assertion on `geo/projectjjk` | VERIFIED |
| ClickGui registers exactly two modules | JujutsuModules.registerAll | VERIFIED |
| Boom detonates all loaded owned nails with no aim gate | ProjectJjkRitualRuntime.collectAllLoadedOwnedNails | VERIFIED |
| ProjectJJK assets are temporary permitted placeholders | user decision, legal import note | VERIFIED for private development |
| Rich provenance is release-ready | no durable permission/license found | UNKNOWN |
| In-game visual feel is correct | no current smoke evidence | UNKNOWN |
