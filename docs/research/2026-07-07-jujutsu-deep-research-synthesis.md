# Jujutsu Deep Research Synthesis

## Source Documents

User-provided research files reviewed on 2026-07-07:

- `docs/research/sources/2026-07-07-combat-ability-design.md`
- `docs/research/sources/2026-07-07-fabric-technical-bible.md`
- `docs/research/sources/2026-07-07-jujutsu-kaisen-minecraft-bible.md`

These documents are now project orientation material. They define design direction, vocabulary, quality bars, and research leads. They do **not** replace checking Fabric/Minecraft 1.21.8 APIs before implementation.

Later VFX-specific corpus:

- `docs/research/sources/2026-07-07-fabric-vfx-development.md`
- `docs/research/sources/2026-07-07-hairpin-vfx-ux-bible.md`
- `docs/research/sources/2026-07-07-fabric-combat-architecture.md`
- `docs/research/sources/2026-07-07-fabric-1218-vfx-production-bible.md`

VFX/combat-focused synthesis:

- `docs/research/2026-07-07-fabric-vfx-combat-research-synthesis.md`

## Standing Project Principles

1. Build one excellent vertical slice before expanding the roster.
2. Readability beats raw spectacle: a player must understand threat, timing, direction, and counterplay.
3. VFX language is semantic: color, shape, sound, camera, and screen effects must carry stable meanings.
4. A move is defined by timing and commitment, not only damage: startup, active window, recovery, punishability, cost.
5. Complexity should come from risk, timing, spacing, and choice, not from visual noise.
6. High-value moves require readable cost: visible startup, range limits, recovery, resource cost, or positional risk.
7. Hitbox and visual shape must agree.
8. Hitstop, camera shake, FOV shifts, and screen effects are impact tools, but must be local-only and restrained for multiplayer.
9. Gameplay authority stays server-side: damage, hits, cooldowns, costs, resources, valid targets, and persistent state.
10. Client code owns transient feel: particles, camera accents, HUD flash, screen effects, interpolation, local playback.
11. Networking should send semantic events/state, not per-particle or per-frame visual data.
12. Ability implementation should trend toward explicit state machines and data/tags, not ad hoc tick branches.
13. Fabric architecture must maintain strict common/client separation.
14. Prefer Fabric public APIs and events before Mixins.
15. Every effect must be judged by cost class: tick, packet, allocation, GPU upload, transparency/fill-rate, and renderer compatibility.

## Immediate Hairpin Implications

Nobara/Hairpin should be treated as a nail-based, warning-first detonation technique:

- Core fantasy: nails are placed into blocks/entities, vibrate and glow, then detonate.
- Visual ingredients: fuchsia cursed energy, rusted metal, nail shards, black powder smoke/residue.
- The current dark blood-black direction is acceptable if it preserves a distinct fuchsia/hot-energy read at the detonation core.
- Nails must read as intentional markers of danger, not random decoration.
- Bloom and afterglow should be one continuous effect: detonation expands, then the same shards/residue decay.
- Hairpin must never look like a recolored TNT explosion.
- Multiplayer readability matters: embedded nails should warn nearby players before the burst.
- Server should store/choose nail positions and authorize detonation; client derives glow, vibration, particles, shock ring, smoke/residue, HUD/camera accents.

Additional details from the copied first corpus:

- Nobara's visual identity combines rusted metal, straw-doll lineage, black powder smoke, and fuchsia cursed energy.
- Hairpin's embedded nails should vibrate/glow before detonation; this is the readable warning, not decorative clutter.
- The detonation timing reference remains useful: nail placement `1 tick`, detonation phase `15 ticks`, explosion `1 tick`, fade `8 ticks`.
- Standard TNT-style explosions are explicitly rejected.
- Future shader language can include distortion, shock wave, bloom simulation, outlines, and radial blur, but these are R&D implementation layers after the visual grammar is approved.

Research timing target from the user bible:

- nail placement: 1 tick;
- detonation wind-up: 15 ticks;
- burst: 1 tick;
- decay: 8 ticks.

This is a useful visual reference, not final gameplay balance. Current `HairpinTimeline` is longer and more cinematic for the standalone prototype; future gameplay timing can be tightened after smoke testing.

## Fabric Architecture Implications

Use the current project's boring explicit structure, but let these boundaries guide growth:

- `src/main`: registries, gameplay state, typed payloads/codecs, command/debug triggers, damage/hit logic, resources/cooldowns.
- `src/client`: receiver handlers, local playback, particles, HUD, camera/screen effects, client renderers.
- Networking: central typed payload registration; serverbound packets must validate player, range, entity/world state, and costs.
- VFX: server sends one semantic event per scene; clients spawn transient visuals locally.
- Particles: prefer standard Fabric particle type + client provider + sprite JSON first.
- Custom world renderers, post effects, marker-particle shader protocols, and dynamic lighting are separate R&D tasks, not assumed baseline.
- Render code for Minecraft 1.21.8 should respect the extraction/drawing split where applicable. Drawing should not reach back into mutable gameplay state.
- Add larger packages only when the vertical slice needs them: `ability`, `combat`, `energy`, `client.rendering`, `client.camera`, `data`.

## Source Trust Rules

Treat these research docs as design direction, but classify claims before implementation:

- **Binding project direction:** readability, server authority, semantic visual language, one polished slice first, no visual spam, no vanilla TNT-like Hairpin.
- **Implementation hypotheses:** marker particles, cel-shading, Sobel outlines, distortion shaders, Display Entity animation, dynamic lighting, chunk render pools.
- **Must verify before coding:** Fabric class names, method names, render APIs, networking signatures, particle APIs, shader/post-processing hooks, mappings, and version-specific behavior.
- **Inspiration-only:** Reddit, Fandom/Wiki, Paper/Forge/NeoForge/Bedrock examples, generalized anime analysis, and non-Fabric shader tutorials.
- **Primary sources for code:** local 1.21.8 Fabric/Minecraft jars, official Fabric docs for the exact version, Fabric API source, Mojang/Yarn/official mappings as used by this project.

## Recommended Repo Docs To Add Next

These are worth splitting out later when the project grows:

- `docs/design/combat-principles.md` — readable combat, timing grammar, risk/reward, hitstop, PvP/PvE differences.
- `docs/design/vfx-language.md` — semantic VFX vocabulary, attention hierarchy, color/shape/audio rules.
- `docs/design/characters/nobara.md` — Nobara kit bible, Hairpin/Resonance, materials, palette, timing, counterplay.
- `docs/architecture/fabric-1.21.8.md` — common/client split, networking, rendering model, particles, datagen, anti-patterns.
- `docs/architecture/client-effects.md` — server semantic event -> client transient effect rule.
- `docs/verification/ability-checklist.md` — clarity, fairness, expression, counterplay, and readability-under-chaos passes.

Do not create all of these immediately unless they are needed for the next implementation step.

## Effect Checklist For Future Hairpin Iterations

Before a Hairpin change is accepted:

- Can the viewer identify the nails before detonation?
- Does the detonation point and radius read clearly?
- Do bloom and afterglow feel like one continuous event?
- Are secondary particles quieter than the primary threat shape?
- Does audio have separate roles: pressure, hammer/nail transient, burst body, decay tail?
- Is gameplay authority still server-side?
- Is the client deriving transient visuals locally from one semantic event?
- Has the changed visual been shown to the user before porting it into gameplay code?
