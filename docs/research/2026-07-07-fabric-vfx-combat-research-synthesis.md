# Fabric VFX And Combat Research Synthesis

## Source Documents

User-provided research files reviewed and copied into the repository on 2026-07-07:

- `docs/research/sources/2026-07-07-fabric-vfx-development.md`
- `docs/research/sources/2026-07-07-hairpin-vfx-ux-bible.md`
- `docs/research/sources/2026-07-07-fabric-combat-architecture.md`
- `docs/research/sources/2026-07-07-fabric-1218-vfx-production-bible.md`

The copied files are raw research inputs. Keep them unchanged unless the user provides a corrected version. Some snippets contain research-tool citation/span artifacts, so implementation must still verify API names locally against this project and Fabric/Minecraft 1.21.8 before code is written.

## Binding Direction

These reports strengthen the existing project direction:

1. Hairpin is not a generic explosion. It is a nail-anchored remote detonation technique.
2. The first polished slice should be Nobara/Hairpin, not a broad combat framework.
3. Gameplay stays server-authoritative: damage, target validation, cooldowns, costs, hit state, nail anchors, and detonation eligibility belong to the logical server.
4. Client code owns transient presentation: particles, world-space geometry, screen flash, camera feel, local interpolation, and audio playback.
5. Networking should send semantic events with deterministic seeds, not per-particle or per-frame visual state.
6. Visual readability is gameplay. Threat shape, wind-up, direction, radius, and residue must all be readable under poor visibility and multiplayer chaos.
7. Avoid direct OpenGL calls, render-thread allocations, and unbounded translucent particle clouds.
8. Prefer native Fabric/Minecraft rendering, particles, payloads, resources, and datagen before adding graphics libraries.

## Hairpin VFX/UX Rules

The Hairpin bible gives the strongest concrete art direction. Treat the canonical gameplay grammar as:

`mark -> warn -> compression -> snap -> burst -> residue`

Practical consequences:

- The nail is the primary threat marker. The viewer should understand where danger is before detonation.
- Bloom and afterglow must be one continuous event driven by the same nail anchor and timeline curve. They should not feel like two unrelated systems taking turns.
- The main burst should be spike/fracture shaped, not a TNT sphere, fireball, or smoke dome.
- The palette should be darker and meaner than the current magenta-heavy prototype: roughly 60-70% dark carmine / black cherry / blood-black, 15-25% dirty fuchsia or magenta edge energy, and 10-15% cold metal.
- Straw motifs are secondary lineage references. They should not dominate the Hairpin blast.
- Residue should be short, 6-12 ticks in gameplay reads, enough to preserve visual memory without obscuring follow-up combat.
- Crooked nails are only acceptable when they read as intentionally embedded into a surface or body. If they look randomly misaligned, the design loses craft and threat clarity.
- For a visual target, show both a "head finisher" and "ground cluster" variant when possible.

Open design choices before gameplay implementation:

- Are nail anchors consumed all at once or by selected cluster?
- Do block nails and entity nails share one visual state or have different residue/material response?
- Are entity nails attached to hitbox segments, model anchors, or a simplified server-side attachment point?
- Does PvP allow friendly fire or teammate-safe detonations?
- Does Hairpin and future Resonance share an input, or does Hairpin require its own detonation command?

## Timing Model

Use Minecraft's 20 TPS rhythm as the gameplay authority:

- 1 tick = 50 ms.
- For cinematic visual targets, Hairpin can be slower: anticipation 8-12 ticks, compression 2-3 ticks, detonation 1-2 ticks, decay 10-16 ticks.
- For gameplay, tighten to 12-18 ticks total: anticipation 4-6 ticks, compression 1 tick, detonation 1 tick, decay 6-10 ticks.
- Do not give Hairpin a long beam-like charge. The identity is a sharp remote snap after setup.

The current standalone prototype can remain more cinematic while the visual language is being tuned. Gameplay implementation should expose timing as data or constants that are easy to rebalance.

## Combat Architecture

The combat architecture report recommends a small explicit ability state machine:

- `IDLE`
- `STARTUP`
- `ACTIVE`
- `RECOVERY`

For Hairpin specifically, map those phases to:

- setup state outside the detonation ability: embedded/armed nails exist on server
- startup: warn/pre-bloom/audio cue
- active: detonation validation, damage, posture/stagger, server-side nail spending
- recovery: player commitment and cancel windows
- residue: client-only visual tail, not a gameplay authority window unless a lingering hazard is intentionally added

First-slice components should stay minimal:

- a nail projectile or debug nail-anchor placement path
- server-side nail anchor state
- a Hairpin detonation action
- typed payloads for semantic VFX events
- client playback timeline for Hairpin VFX
- focused tests around timeline math, state transitions, and server validation

Avoid a universal JSON ability interpreter for the first slice. If data is introduced, keep it to tuning constants, resource costs, sound/particle identifiers, and timing windows. The Java code should own the bespoke Hairpin behavior until one vertical slice proves the pattern.

## Networking Rules

Recommended event shape:

- server validates ability request, resources, cooldowns, target/nail state, and line-of-sight/range where relevant
- server computes authoritative detonation tick and random seed
- server broadcasts one semantic event to tracking players
- each client locally reconstructs particles, ribbons, audio, screen pulse, and camera accent from the same seed and anchor data

Do not trust the client for:

- damage values
- successful hits or block piercing
- current health, armor, cursed energy, or cooldowns
- active nail count or detonation eligibility
- status effects or stagger decisions

The reports repeatedly warn against per-particle networking. A Hairpin effect should be replicated as meaning, not as hundreds of coordinates.

## VFX Implementation Rules

Use a layered VFX vocabulary:

- particles: sparks, dust chips, black-red motes, small residue particles
- world-space geometry: nail markers, spike bursts, crack lances, rings, ribbons, trails
- display/entity rendering or custom world rendering: persistent embedded nails
- HUD/screen: local owner/victim pulse only, restrained for observers
- audio: separate transient, body, and tail layers

Implementation constraints:

- Keep client-only render/HUD/camera code in `src/client`.
- Register particle types in common code and factories in client code.
- Use sprite JSON under `assets/<modid>/particles/` and textures under `assets/<modid>/textures/particle/`.
- Use OGG Vorbis audio. Positional world sounds must be mono.
- Avoid direct LWJGL/OpenGL calls. Use Fabric/Minecraft rendering abstractions and `VertexConsumer`/buffer paths.
- Avoid allocations in render callbacks and particle `tick()` loops.
- Use short lifetimes and LOD budgets for combat particles.
- Screen/camera effects should be configurable or at least easy to disable/reduce later for accessibility and PvP comfort.

## API Verification Notes

The reports contain useful version-specific claims, but code should verify them locally before implementation. Treat these as checkpoints, not copy-paste truth:

- `CustomPacketPayload` and `StreamCodec` payload wiring.
- `ResourceLocation.fromNamespaceAndPath(...)` or the mapping-equivalent identifier constructor used by this project.
- `HudElementRegistry` and `Matrix3x2fStack` for 1.21.8 HUD rendering.
- `WorldRenderEvents` event names and context accessors for this Fabric API version.
- `BufferBuilder` / `VertexConsumer` method names such as `addVertex`, `setColor`, `setUv`, `setLight`.
- Any camera shake, FOV pulse, post-processing, or shader hook, because these are likely to require local mapping checks or Mixins.
- Any `FabricRenderState` or extraction/drawing-stage integration detail.

For code, the local Gradle dependencies and official Fabric docs/source are higher authority than these research snippets.

## Dependency Direction

For the first Hairpin implementation:

- Avoid Veil/Lodestone/GeckoLib unless a later design proves they save more maintenance cost than they add.
- Keep Satin/post-processing optional and later-stage. It may be useful for Black Flash or high-end screen effects, but it is not required for the first Hairpin vertical slice.
- Design for Sodium/Iris compatibility from the start by staying inside standard rendering abstractions.

## Production Asset Checklist

Assets worth creating before or during the first real Hairpin slice:

- plain nail model
- armed nail model with emissive/dark rim treatment
- cursed spike mesh for finisher silhouette
- narrow and fan-shaped burst ribbon/mesh variants
- particle sprites: sharp shard, dust chip, blood-black droplet, dirty-magenta spark
- residue sprite atlas with staged decay
- ready/armed nail HUD icons
- SFX: nail set, metallic ping, cursed hum, snap trigger, dry burst, gritty tail
- block material response set: stone, wood, dirt
- lightweight victim overlay

The visual target should be used to validate palette, silhouette, and timing before these assets are ported into real Fabric render code.

## Verification Checklist For Hairpin Work

Before calling a Hairpin iteration done:

- The nail marker is readable before detonation.
- The burst clearly originates from the nail, not from an abstract sphere.
- Bloom and afterglow share one visual timeline.
- The afterglow is short and feels like residue from the same event.
- The palette reads dark carmine / blood-black first, dirty fuchsia second, cold metal third.
- The warning phase is readable at distance and in bad visibility.
- The main hitbox/threat shape matches the visual shape.
- The server authorizes damage, costs, cooldowns, and nail spending.
- The client receives semantic events and derives transient VFX locally.
- No render path relies on direct OpenGL calls.
- Particle count, translucent overdraw, and render allocations are bounded.
- Positional sounds are mono.
- Local screen/camera effects do not punish observers or PvP readability.

## Immediate Next Step

Before deeper gameplay code, update the standalone Hairpin visual target one more time against this corpus:

1. Darken accents toward blood-black and black cherry.
2. Make nail placement look intentionally embedded, not randomly tilted.
3. Drive bloom, burst, and afterglow from one timeline object.
4. Keep the afterglow visually connected to the nail anchor with shared color, shard direction, and residue motion.
5. Preserve the current interface, but smooth panel styling and controls only after the core effect reads correctly.

After the user approves that visual target, port the smallest vertical slice into Fabric with server-owned nail anchors and client-owned playback.
