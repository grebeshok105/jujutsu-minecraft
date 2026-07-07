# Hairpin VFX Canonical Profile

Status: accepted after ultra review.

Source inputs:

- `docs/research/2026-07-07-hairpin-vfx-production-implementation-brief.md`
- `docs/reviews/2026-07-07-ultra-review.md`
- in-game feedback from the particle workbench pass

## Goal

Hairpin should read as four cursed nails folding pressure into one impact point, then leaving a restrained blood-black residue. It must not read as generic smoke, a firework, or a loose pile of unrelated particle sprites.

## Timing

The runtime timeline is tick-driven from the server payload `startGameTime`.

- Prep freeze: establish nail anchors and the target mark.
- Hammer snap: short warning edge, not a lingering aura.
- Nail ignition: visible compression from nails toward the target.
- Hairpin bloom: compact fracture burst at the target with metal shards as accents.
- Afterglow: fading residue on the same burst vectors, never a new unrelated cloud.

## Palette

Primary read is blood-black and dark iron.

- Dirty fuchsia is an accent only, capped by `HairpinVisualProfile.dirtyFuchsiaMaxVisiblePercent()`.
- The dominant mass should remain near black, oxblood, steel, and ember red.
- Pure white flash is allowed only as a very brief impact readability cue.

## Particle Budget

Particle counts are controlled by `HairpinVisualProfile`.

- Bloom should stay dense enough to register in daylight.
- Bloom should stay below the clutter range; current test gate is 36-52 scheduled particles per tick.
- Afterglow should be lighter than bloom and inherit bloom direction.
- Metal shards are accents, not the main body of the effect.

## Verification Gate

Before Hairpin becomes the combat template, it needs an in-game capture pass:

- anchors are visible before the snap
- compression visibly moves toward the target
- bloom is compact and readable
- afterglow feels like a continuation, not a separate effect
- no missing sound/texture warnings
- repeated triggers do not flood the screen or logs
