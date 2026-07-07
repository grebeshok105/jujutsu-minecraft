# Ultra Review Adoption Plan

Source review: `docs/reviews/2026-07-07-ultra-review.md`

## Verdict

The ultra review is accepted as the current project correction map. It correctly identifies that the project should stop tuning particles in isolation and stabilize the Hairpin vertical slice workflow before moving into combat.

## Immediate Decisions

- Treat `chore/jujutsu-brainstorming` as the current source of truth until it is merged into `main`.
- Remove Fabric template mixins because they add unused injection surface.
- Keep the Hairpin-first vertical slice.
- Keep semantic server payload plus client-side VFX playback.
- Keep shaderless readability as the primary requirement; GLSL assets are source assets until wired into a tested runtime path.
- Stop claiming VFX readiness from compile/jar packaging alone.
- Require in-game visual capture before moving to combat.

## Immediate Work

1. Remove template mixins and template metadata.
2. Add resource/import sanity tests:
   - registered particle JSON exists
   - particle JSON texture assets exist
   - sound entries do not point at missing files
   - no `fabric.impl` imports
   - no client imports from `src/main/java`
3. Gate Hairpin diagnostic logs behind a debug switch.
4. Move Hairpin playback timing toward tick-based elapsed time using the existing `startGameTime`.
5. Create a canonical Hairpin VFX spec and use it as the single design source for the next VFX pass.
6. Merge the current worktree branch back into `main` after verification, so the root checkout builds the actual Hairpin mod.

## Deferred Work

- Production GLSL runtime pipeline.
- Full data-driven JSON VFX registry.
- Base particle class refactor.
- Combat/ability framework.
- Final nail model/decal system.

## Non-Negotiable Gate Before Combat

Hairpin must be demonstrated in-game with screenshots or video across normal visibility conditions. It must show visible anchors, readable warning/compression/snap/burst/residue staging, no black smoke ball, no missing resources, and acceptable repeated-trigger behavior.
