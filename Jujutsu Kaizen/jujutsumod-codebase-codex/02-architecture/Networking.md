# Networking

← [[00-MOC]] · [[Client-server-boundaries]] · [[../04-client-vfx/VFX-core]] · [[../05-reference/Claim-Source-Index]]

Prefix: `.worktrees/nobara-cinematic-slice/`

## Registration

**Source:** `src/main/java/jujutsu/mod/network/JujutsuNetworking.java:17-23`
**Status:** VERIFIED

| Direction | Payload | Register source | Purpose | Status |
|---|---|---|---|---|
| S2C | `VfxCuePayload` | `JujutsuNetworking.java:18` | one typed visual cue: ID, origin, optional anchor, intensity, server time, seed | VERIFIED |
| S2C | `CharacterSelectionSyncPayload` | `JujutsuNetworking.java:19` | selected character sync to client render/UI | VERIFIED |
| C2S | `SelectCharacterPayload` | `JujutsuNetworking.java:20` | GUI character choice | VERIFIED |
| C2S | `NobaraActionPayload` | `JujutsuNetworking.java:21` | R/B/left-click Nobara actions | VERIFIED |

Removed S2C VFX payloads: `ProjectJjkNobaraImpulsePayload`, `HairpinFxPayload`, `HairpinNailFlightPayload`, and `PreparedNailsPayload`. `ProjectSanityTest.java:357-362` guards the core migration; old legacy guards remain in the same test.

## Server receivers

| Payload | Server path | Source | Status |
|---|---|---|---|
| `SelectCharacterPayload` | `CharacterSelectionManager.select(context.player(), JujutsuCharacter.byId(...))` | `JujutsuNetworking.java:26-28` | VERIFIED |
| `NobaraActionPayload` | `ProjectJjkNobaraActions.tryCast(player, payload.action(), true)` | `JujutsuNetworking.java:29-36` | VERIFIED |

Connection lifecycle:

- join → `CharacterSelectionManager.syncTo(handler.player)`
- disconnect → `CharacterSelectionManager.clear(handler.player)`

## VFX cue broadcast

| Method | Source | Pattern | Status |
|---|---|---|---|
| `broadcastVfxCue` | `JujutsuNetworking.java:38-51` | distance-squared radius filter + `ServerPlayNetworking.canSend` | VERIFIED |
| `sendVfxCue` | `JujutsuNetworking.java:54-59` | direct send gated by `canSend` | VERIFIED |

The server decides whether and when a cue exists. The payload has no gameplay receiver on the client.

## Client receivers

**Source:** `src/client/java/jujutsu/mod/client/network/JujutsuClientNetworking.java:13-19`
**Status:** VERIFIED

| Payload | Client path | Source | Status |
|---|---|---|---|
| `VfxCuePayload` | schedule `VfxDirector.receive(payload.cue())` | `JujutsuClientNetworking.java:14-15` | VERIFIED |
| `CharacterSelectionSyncPayload` | `ClientCharacterSelectionManager.apply` | `JujutsuClientNetworking.java:16-17` | VERIFIED |

The receiver deliberately contains no effect-ID switch. `VfxDirector` finds a registered Java recipe or logs/ignores an unknown ID once.

## Mermaid

```mermaid
sequenceDiagram
  participant C as Client
  participant S as Server
  C->>S: NobaraActionPayload
  Note over S: runtime resolves combat and authority
  S->>C: VfxCuePayload
  Note over C: VfxDirector maps ID to a visual-only recipe
```

## Risks

Nobara combat adds `CurseLinkOptionsPayload` (S2C) and `SelectCurseLinkPayload` (C2S). The server revalidates selected link membership before self resonance; the menu carries identity only and never authorizes damage.

| Risk | Status | Source |
|---|---|---|
| A client outside the broadcast radius sees no local composition | VERIFIED design constraint | `broadcastVfxCue` callers + radius filter |
| `canSend` false silently skips a client | VERIFIED | `JujutsuNetworking.java:46-48,55-57` |
| ID emitted by a server but not registered by a client is ignored safely | VERIFIED | `VfxDirector.java:61-67` |
| Recipe/ID drift needs explicit guard coverage | MITIGATED | `ProjectSanityTest.java:353-356` |

---
tags: #jujutsumod #networking #vfx #verified
