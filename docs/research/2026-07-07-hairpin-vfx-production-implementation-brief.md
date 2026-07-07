# Hairpin VFX Production Implementation Brief

Sources:

- `docs/research/sources/2026-07-07-hairpin-production-art-bible.md`
- `docs/research/sources/2026-07-07-hairpin-particle-production-research.md`
- `docs/research/sources/2026-07-07-hairpin-shader-render-pipeline-research.md`
- `docs/superpowers/specs/2026-07-06-nobara-hairpin-cinematic-design.md`

## Locked Direction

Hairpin is an anchored cursed shrapnel effect. The nail/anchor is the threat, and the burst grows out of that point. The effect must not read as a generic magic sphere, vortex, aura, or fullscreen shader trick.

The approved visual target remains:

1. Marked Nails
2. Trigger Ping
3. Cursed Compression
4. Hairpin Snap
5. Blood-Black Residue

Runtime implementation should map this to the current timeline:

- `PREP_FREEZE`: mark/stain at nails.
- `HAMMER_SNAP`: warning edge and ignition ticks.
- `NAIL_IGNITION`: inward compression motes from nails to the target.
- `HAIRPIN_BLOOM`: directional burst, metal shards, snap crack.
- `AFTERGLOW`: residue from the same burst vectors, not a separate aura.

## Palette

Accent color must stay darker than the first prototype:

- Blood-black base: `#12090c`
- Void black core: `#080607`
- Black cherry body: `#250913`
- Dark carmine wound energy: `#26030a`
- Coagulated residue: `#311016`
- Oxblood shadow edge: `#3a050f`
- Cold steel dark: `#5f666d`
- Cold steel specular: `#98a1aa`

The earlier dirty fuchsia edge was rejected during in-game review as too bright. Runtime Hairpin should use oxblood shadow instead and keep bright red below roughly 4 percent of the visible effect in any frame.

## Particle Families For First Jar

The first production jar should replace the single generic spark with a small sheet-based family:

- `hairpin_mark_stain`: dark anchored stain/crack, world-lit, slow and restrained.
- `hairpin_warn_edge`: short dim oxblood edge pulse.
- `hairpin_compression_mote`: world-lit inward motes, no vortex language.
- `hairpin_snap_crack`: short dim blood-black crack accent.
- `hairpin_burst_residue`: black-red grit body of the burst.
- `hairpin_burst_metal_shard`: cold metal fragments.
- `hairpin_ignition_tick`: tiny dim blood-black activation tick.

Use `ParticleFactoryRegistry`, `FabricParticleTypes.simple(...)`, sheet JSON files, and delayed sprite factories. Do not use `ParticleRenderType.CUSTOM` for the first jar.

## Shader Policy

The effect must look readable without a post shader. The first jar may include GLSL assets for the planned shader stack, but the active runtime path should be particles plus world-space geometry through Fabric render events.

Safe active rendering path:

- Register a client renderer on `WorldRenderEvents.AFTER_ENTITIES`.
- Use `WorldRenderContext.consumers()` and camera-relative coordinates.
- Draw short ribbons/arcs/spikes with existing vanilla render types.
- Avoid raw OpenGL calls, core shader overrides, and mandatory fullscreen framebuffer passes.

Included GLSL assets should be treated as source assets for the next shader pass:

- shared noise/fracture/timeline helpers
- residue dissolve fragment logic
- optional snap edge fragment logic

## Acceptance Criteria

- `gradlew.bat testHairpinTimeline --no-daemon` passes.
- Hairpin visual profile tests pass.
- `gradlew.bat build --no-daemon -x test` produces a remapped mod jar in `build/libs`.
- Particle JSON files and PNG textures are packaged in the jar.
- GLSL files are packaged in the jar.
- Runtime code uses no `net.fabricmc.fabric.impl.*` imports and no raw OpenGL calls.
- Burst and afterglow share vectors: residue fades from the burst, not from an unrelated cloud.
