# Nail Rendering

← [[00-MOC]] · [[03-systems/Nail-entity-lifecycle]]

Prefix: cinematic worktree `src/client/...`

## Entity renderer

**Source:** `JujutsuModClient.java:18`  
`EntityRendererRegistry.register(JujutsuEntities.PROJECTJJK_NAIL, ProjectJjkNailRenderer::new)`  
**Class:** `client/render/ProjectJjkNailRenderer.java`  
**Status:** VERIFIED registration

## State-driven visuals

Entity synced fields (flying/embedded/forward/local attach) drive orientation.  
**Source:** `ProjectJjkNailEntity` data accessors `:25-30`  
**Status:** VERIFIED

## Item models

Item models under `assets/jujutsumod/models/item/hairpin_nail.json` (+ projectjjk alias).  
Used for held item and possibly renderer stack.

## Particles around nails

Prepare phase server sends:

- `HAIRPIN_WARN_EDGE`, `HAIRPIN_IGNITION_TICK`, `SOUL_FIRE_FLAME`  
**Source:** `ProjectJjkNobaraRuntime.prepareNails` `:55-57`  
**Status:** VERIFIED

Impact custom particles: `spawnCustomImpactParticles` `:151`.

## Embedded mark rendering (client)

`TargetMarkRenderManager` renders mark count/expiry from `ProjectJjkTargetMarkPayload`.  
**Status:** VERIFIED class path; exact draw math INFERRED without full file body this pass.

---
tags: #jujutsumod #vfx #nail
