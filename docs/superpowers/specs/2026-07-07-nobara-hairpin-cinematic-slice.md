# Nobara Hairpin Cinematic Slice

Status: accepted for implementation.

Primary research:

- `docs/research/sources/2026-07-07-minecraft-cinematic-vfx-bible.md`
- `docs/superpowers/specs/2026-07-07-hairpin-vfx-canonical-profile.md`

## Goal

Hairpin should become the first cinematic character slice: a readable anime-style beat built from items, world geometry, particles, camera impulse, and screen effects. The result should feel like cursed nails folding pressure into one violent impact, not like loose particle smoke.

## Decisions

- Build a Nobara slice first, not a generic VFX framework in isolation.
- Keep server authority on the existing `HairpinFxPayload`.
- Keep rendering, camera, overlays, and cinematic playback in `src/client`.
- Add real item identities for `hairpin_nail` and `straw_doll_hammer`.
- Use Minecraft JSON item models for inventory/held/world presentation in this pass.
- Use deterministic generated pixel textures for the first jar; skin-specific art can replace them later.
- Add camera/screen effects as client-only approximation before attempting invasive post-process injection.
- Keep GLSL files as source assets unless Fabric 1.21.8 runtime wiring is proven by compilation.

## VFX Beats

- Mark: nails and target are staged, dark red vignette starts.
- Warning: short edge cue and camera tension.
- Compression: nail lines pull inward, FOV/roll bias implies speed.
- Snap/Burst: impact flash, camera impulse, shockwave ring, fracture quads, compact residue.
- Afterglow: residue fades along the burst vectors; no separate black cloud.

## Interfaces

- Items:
  - `jujutsumod:hairpin_nail`
  - `jujutsumod:straw_doll_hammer`
- Commands:
  - `/jujutsu give nobara_tools`
  - existing Hairpin commands remain the cinematic test surface.

## Verification

- `gradlew.bat check build --no-daemon -x test`
- Resource sanity must cover item models and textures.
- In-game smoke:
  - `/jujutsu give nobara_tools`
  - `/jujutsu hairpin`
  - `/jujutsu hairpin stage burst`
  - `/jujutsu debug hairpin true`

## Deferred

- Full combat framework.
- Skin-aware character renderer.
- Production post-process chain with true velocity-buffer motion blur.
- Data-driven VFX presets.
