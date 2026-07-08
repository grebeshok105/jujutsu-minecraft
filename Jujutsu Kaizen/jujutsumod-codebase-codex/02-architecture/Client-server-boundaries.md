# Client–Server Boundaries

← [[00-MOC]]

## Server-authoritative (do not move to client)

| Concern | Where | Status |
|---|---|---|
| Nail prepare/launch/impact damage | `ProjectJjkNobaraRuntime` | VERIFIED |
| Marks apply/consume | `ProjectJjkNailMarks` + `RitualRuntime.markTarget` | VERIFIED |
| Detonate / enlarge / resonance | `ProjectJjkRitualRuntime` | VERIFIED |
| Pending explosion/enlarge queues | `PENDING_*` + server tick | VERIFIED |
| Character selection state | `CharacterSelectionManager` | VERIFIED |
| Commands | `JujutsuCommands` | VERIFIED |

## Client-only

| Concern | Where | Status |
|---|---|---|
| Particle factories | `client/particle/*` | VERIFIED |
| Nail entity renderer | `ProjectJjkNailRenderer` | VERIFIED |
| HUD flash / world ribbons | `HairpinScreenOverlay`, `HairpinWorldRenderer` | VERIFIED |
| Cinematic camera mixins | `HairpinCameraMixin`, `HairpinGameRendererMixin` | VERIFIED |
| Character select UI | `gui/CharacterSelectScreen`, `ui/*` | VERIFIED |
| Skin mixin | `CharacterSkinMixin` | VERIFIED |
| Impulse→SFX/VFX mapping | `JujutsuClientNetworking` | VERIFIED |

## Shared / both

| Concern | Notes | Status |
|---|---|---|
| Payload records | main sourceset, used both sides | VERIFIED |
| `HairpinTimeline` / `HairpinVisualProfile` | main; used by client playback | VERIFIED |
| Entity synced data on nail | server sets, client interpolates | VERIFIED entity data accessors |

## Rules (AGENTS.md + code)

- No gameplay authority on client.
- Client under `src/client` only for render/input/HUD.
- Public Fabric API only; mixins only where listed.

---
tags: #jujutsumod #boundaries
