# Client-Server Boundaries

<- [[00-MOC]] | [[Networking]] | [[../04-client-vfx/VFX-core]]

## Server-authoritative (do not move to client)

| Concern | Where | Status |
|---|---|---|
| Nail prepare/launch/impact damage | `ProjectJjkNobaraRuntime` | VERIFIED |
| Marks apply/consume | `ProjectJjkNailMarks` + `ProjectJjkRitualRuntime.markTarget` | VERIFIED |
| Detonate / Enlarge / resonance resolution | `ProjectJjkRitualRuntime` | VERIFIED |
| Pending explosion/enlarge queues | `PENDING_*` + server tick | VERIFIED |
| Character selection state | `CharacterSelectionManager` | VERIFIED |
| VFX cue creation, server time, and seed | Nobara runtime / ritual server methods | VERIFIED |
| Commands | `JujutsuCommands` | VERIFIED |

## Client-only

| Concern | Where | Status |
|---|---|---|
| Recipe lookup, instance lifetime, unknown-ID safety | `client/vfx/VfxDirector` | VERIFIED |
| Ring/ribbon/blade geometry | `VfxWorldChannel` via one `AFTER_ENTITIES` callback | VERIFIED |
| Local particles, sound, HUD, camera/FOV, first-person motion | director channels | VERIFIED |
| Nail entity renderer and persistent aura | `ProjectJjkNailRenderer` + `VfxPalette` | VERIFIED |
| Existing narrow camera and first-person mixins | read `VfxDirector` state only | VERIFIED |
| Character select UI / skin mixin | existing client UI/render code | VERIFIED |

## Shared / both

| Concern | Notes | Status |
|---|---|---|
| `VfxCue` | main-source immutable visual event; origin is fallback for a gone anchor | VERIFIED |
| `VfxCuePayload` | main-source typed S2C codec | VERIFIED |
| Effect IDs | `NobaraVfxIds` is main-source shared vocabulary | VERIFIED |
| Nail synced entity data | server sets; client interpolates and renders | VERIFIED |

## Boundary rules

- Client recipes are visual-only: they do not damage, consume marks, modify cooldowns, spawn authoritative entities, or send gameplay packets.
- Server remains the only source of cue timing, seed, target state, and combat result.
- Recipes use `VfxContext` channels; they do not create packet handlers, global render callbacks, HUD registrations, or mixins.
- Client rendering stays under `src/client`; shared cue/codec types stay under `src/main`.
- Public Fabric API only; no new broad mixins.

---
tags: #jujutsumod #boundaries #vfx #verified
