# Claim-to-Source Index

Status: CURRENT

| Claim | Source | State |
|---|---|---|
| Fabric 1.21.8 / Java 21 | gradle.properties, build.gradle, fabric.mod.json | VERIFIED |
| N opens the only product menu | JujutsuKeybinds.register, ClickGui | VERIFIED |
| Characters panel applies a server selection | CharacterRosterPanel.applySelection, SelectCharacterPayload | VERIFIED |
| Selection persists and starter claim is one-time | CharacterPlayerState, JujutsuAttachments, CharacterSelectionManager.select | VERIFIED |
| C2S actions execute on server thread | JujutsuNetworking.registerServerReceivers | VERIFIED |
| Nobara actions require selected Nobara | ProjectJjkNobaraActions.tryCast | VERIFIED |
| Hairpin uses concrete loaded owner nails | EmbeddedNailRegistry.loadedOwnedNails, ProjectJjkRitualRuntime | VERIFIED |
| Embedded nail TTL/cap are 1200/30 | ProjectJjkNobaraProfile | VERIFIED |
| Resonance changes global server TPS | ProjectJjkStrawDollRuntime.resolveImpact, ServerTimeDilation | VERIFIED and accepted |
| VFX uses one cue/director/recipe path | VfxDirector, JujutsuClientNetworking, NobaraVfxRecipes | VERIFIED |
| Nobara defines 25 VFX ids | NobaraVfxIds | VERIFIED |
| Client mixin count is 6 | jujutsumod.client.mixins.json | VERIFIED |
| ProjectJJK assets are temporary permitted placeholders | user decision, legal import note | VERIFIED for private development |
| Rich provenance is release-ready | no durable permission/license found | UNKNOWN |
| In-game visual feel is correct | no current smoke evidence | UNKNOWN |
