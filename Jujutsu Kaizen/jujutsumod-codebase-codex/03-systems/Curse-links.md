# Curse Links

← [[00-MOC]] · [[Nobara-runtime-flow]] · [[Combat-timing-and-black-flash]] · [[../02-architecture/Networking]] · [[../05-reference/Public-api-surface]]

## Purpose

`CurseLink` is an explicit server-owned relationship: immutable link id, source owner UUID, technique ID, participant UUID set, and creation game time. A status effect, local UI state, or client request cannot create one. Links remain in memory through participant unload/disconnect; their source technique owns removal.

**Source:** `CurseLink.java:7`, `CurseLinkRegistry.java:12-36`. **Status:** VERIFIED.

## Registry contract

| API | Meaning |
|---|---|
| `createLink` | creates and stores a link with a generated UUID |
| `removeLink` | removes exactly one link |
| `removeLinksOwnedBy` | lets the source technique clear its links |
| `linksForParticipant` | returns a deterministic creation-time/id sorted immutable list |

The global registry clears only on server stop. Current development commands create/list/clear links under `/jujutsu curse_link`; these are diagnostic tools, not a player-progression system.

**Source:** `CurseLinkRegistry.java:12-36`, `JujutsuCommands.java:43-73`, `JujutsuMod.java:38`. **Status:** VERIFIED.

## Selection and self resonance

`SelfResonanceRuntime.tryCast` resolves the player's participant links:

1. no valid link: server sends a localized failure;
2. exactly one link: it is selected automatically;
3. multiple links or stale selection: server sends `CurseLinkOptionsPayload`, and the client opens `CurseLinkSelectionScreen`;
4. `SelectCurseLinkPayload` reaches `SelfResonanceRuntime.select`, which rechecks membership and requires at least two current links;
5. the next Shift+R begins the 14-tick windup.

The pending cast stores the link UUID, looks it up again at impact, and revalidates the caster's membership. The caster takes 6 self-resonance damage first. Only after that accepted hit do loaded living linked participants take 18 damage, heavy stagger, and VFX.

**Source:** `SelfResonanceRuntime.java:24-106`, `CurseLinkOptionsPayload.java:12-27`, `SelectCurseLinkPayload.java:9-13`, `JujutsuClientNetworking.java:21-24`. **Status:** VERIFIED.

## Limits

The registry is currently in-memory: server restart clears links. It has no persistence, expiry, world/dimension policy, or ordinary gameplay source besides development commands. Those are product decisions, not implemented facts; see [[../01-meta/Uncertainties]] and [[../06-maintenance/Risks-and-tech-debt]].

---
tags: #jujutsumod #curse-link #self-resonance #verified
