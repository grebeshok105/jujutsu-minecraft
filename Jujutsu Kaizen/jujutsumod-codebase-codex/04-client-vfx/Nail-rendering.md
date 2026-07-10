# Nail Rendering

← [[00-MOC]] · [[../03-systems/Nail-entity-lifecycle]] · [[Hairpin-effects]] · [[VFX-core]]

Prefix: cinematic worktree `src/client/...`

## Entity renderer

**Source:** `JujutsuModClient.java:16`
`EntityRendererRegistry.register(JujutsuEntities.PROJECTJJK_NAIL, ProjectJjkNailRenderer::new)`  
**Class:** `client/render/ProjectJjkNailRenderer.java`  
**Status:** VERIFIED

## State-driven visuals

Entity synced fields drive orientation and embedded attachment.

| Synced / state field | Role | Source | Status |
|---|---|---|---|
| flying / embedded flags | decide flying, prepared, embedded render behavior | `ProjectJjkNailEntity.java` data accessors | VERIFIED |
| forward vector | orient nail along launch/embed direction | `ProjectJjkNailRenderer.java:56-63` | VERIFIED |
| embedded target id/local offset/local forward | render nail at victim body-space anchor | `ProjectJjkNailRenderer.java:65-74,82-95` | VERIFIED |

## Nail aura

Prepared/flying nails use a local blue force-field envelope in the real entity renderer. This is intentionally **not** a transient VFX Core recipe: the aura must follow the authoritative entity state through flight and attachment.

| Claim | Status | Source |
|---|---|---|
| Non-embedded nails render `renderCompressedEnergyAura`. | VERIFIED | `ProjectJjkNailRenderer.java:85-92` |
| The rim/tip/bands/slivers/tail envelope is local to the entity pose stack. | VERIFIED | `ProjectJjkNailRenderer.java:117-183` |
| Embedded nails keep only opaque item model and body-space anchor. | VERIFIED | `ProjectJjkNailRenderer.java:82-95` |
| Aura colors share VFX Core’s cursed-energy palette. | VERIFIED | `ProjectJjkNailRenderer.java:23,31-42`; `VfxPalette.java` |
| Aura and runtime do not use `ParticleTypes.SOUL_FIRE_FLAME` or the ignition-tick composition. | VERIFIED | `ProjectSanityTest.java:543-552` |

## Particles around nails

Prepare/flight/impact server particles remain authoritative combat feedback, layered with transient VFX Core composition when a cue is emitted.

| Event | Source | Status |
|---|---|---|
| prepared nail feedback | `ProjectJjkNobaraRuntime.prepareNails` | VERIFIED |
| launch trail | `spawnNailLaunchParticles`, `spawnNailFlightTrail` | VERIFIED |
| piercing/explosive impact | `spawnPiercingImpactFeedback`, `spawnCustomImpactParticles` | VERIFIED |

## Target mark rendering

Target mark visual is vanilla Glowing colored cyan through a temporary scoreboard team. There is no `ProjectJjkTargetMarkPayload` / `TargetMarkRenderManager` route.

---
tags: #jujutsumod #vfx #nail #verified
