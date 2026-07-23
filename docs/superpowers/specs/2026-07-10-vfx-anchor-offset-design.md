# VFX Anchor Offset Fix Design

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-10
Status: Approved by user

## Problem

`VfxAnchorResolver` currently replaces `VfxCue.origin()` with `entity.position()` whenever `anchorEntityId` resolves. This loses the original difference between the effect point and the entity feet. A cue created at `caster.getEyePosition()` therefore resolves at the caster's feet while the entity is alive.

The current Nobara `detonate` recipe makes this visible: its caster cue is created at eye height, but its particles and ring use the resolved origin.

## Decision

Add a world-space `Vec3 anchorOffset` to `VfxCue`.

For an anchored cue, the server computes:

```java
anchorOffset = origin.subtract(anchor.position());
```

The client resolves:

```java
liveAnchor.position().add(cue.anchorOffset())
```

If the anchor is absent or despawned, resolution continues to return the immutable original `cue.origin()`.

Unanchored cues always use `Vec3.ZERO` as their offset.

## Data Flow

`VfxCuePayload` serializes the new vector immediately after `anchorEntityId`. Server and client use the same mod version, so no compatibility adapter or second payload type is added.

The two server-side cue helpers accept an `Entity` for anchored cues rather than a raw entity ID. This keeps offset calculation at the authoritative creation point and prevents callers from forgetting the anchor's source position.

## Scope

Change only:

- `VfxCue` and its typed payload codec.
- `VfxAnchorResolver`.
- Nobara's four anchored cue call sites: hammer, resonance channel, detonate, and first-person snap.
- Focused resolver/codec tests and affected codebase documentation.

## Non-goals

- No feet/center/eyes attachment enum.
- No bone or hand attachment system.
- No rotation of the offset with entity yaw or pose.
- No camera, HUD, first-person, world-channel, or lifecycle refactor.
- No changes to unanchored Enlarge/Explosion behavior.

World-space offset is sufficient for the confirmed eye/center displacement bug. A rotating local attachment is a separate future design.

## Verification

- A moving anchor preserves a non-zero eye-height offset.
- A zero-offset anchored cue still resolves exactly to the live anchor.
- A missing anchor still falls back to the original origin.
- Payload round-trip preserves `anchorOffset`.
- All assertion tasks, `check`, and runtime build pass.
- The final runtime jar is copied to the user's instance and hash-compared.
