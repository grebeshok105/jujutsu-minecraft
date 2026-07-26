# Uncertainties

Status: CURRENT

| Topic | State | How to resolve |
|---|---|---|
| In-game rendering and combat feel | UNKNOWN in headless review | Run a real client smoke test |
| Rich-Modern redistribution rights | UNKNOWN | Record upstream license/permission or replace derived content |
| Public redistribution scope for ProjectJJK placeholders | INFERRED from author permission | Preserve scope durably or replace assets |
| Post-process quality on all target GPUs | UNKNOWN | Test representative hardware and fallback paths |
| Global Resonance hit-stop on larger servers | accepted only for 1–2 players | Revisit if product target changes |

## Resolved

| Topic | Resolution | Source |
|---|---|---|
| Second character and required abstractions | VERIFIED — Todo (Aoi Todo) shipped as the real second kit and the seams were extracted from it: the render stack (CharacterGeoRenderer / CharacterGeoRenderers / CharacterPlayerGeoRenderer / CharacterPlayerGeoModel / CharacterHeldItemLayer), the shared CharacterAbility / CharacterAbilityExecutor slot, and now the per-vessel definitions — CharacterDefinition / JujutsuCharacters on the server, CharacterClientDefinition / JujutsuCharacterClients on the client | JujutsuCharacters.definition; JujutsuCharacterClients.definition; [Vessel definitions](../02-architecture/Vessel-definitions.md); [Vessel render stack](../04-client-vfx/Vessel-render-stack.md) |
