# Next Deep Research Prompts For Hairpin VFX Production

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Use these prompts for additional deep research before the custom particle and shader production phase.

## Prompt 1: Fabric 1.21.8 Custom Particle Production Pipeline

Проведи максимально глубокий technical deep research по production-ready custom particles для Minecraft Fabric 1.21.8 / Java 21 / Mojang mappings, с прицелом на мод `jujutsumod` и технику Nobara Hairpin из Jujutsu Kaisen.

Контекст проекта: мы делаем polished vertical slice Hairpin. Визуальная грамматика утверждается как `mark -> warn -> compression -> snap -> burst -> residue`. Эффект nail-anchored: сервер хранит embedded/armed nail anchors, клиент получает semantic event + deterministic seed и локально воспроизводит VFX. Палитра: blood-black / black cherry / dark carmine, dirty fuchsia только edge, cold metal для гвоздей. Нельзя делать TNT-like explosion, generic smoke sphere или независимый afterglow.

Исследуй:

- Fabric 1.21.8 particle registration: common particle type registration, client factory registration, sprite sets, JSON sprite declarations, exact package/class/method names for Mojang mappings where possible.
- Различия SimpleParticleType vs ParticleOptions/custom payload options: когда нам достаточно simple particles, а когда нужны параметры цвета/scale/lifetime/seed/anchor/material.
- TextureSheetParticle/SpriteSet lifecycle: age, lifetime, alpha, quad size, friction, gravity, no-physics, sprite animation by age.
- ParticleRenderType / ParticleTextureSheet categories: opaque, translucent, lit/no-lit, custom; cost model and batching implications.
- Как сделать конкретные Hairpin particle families: blood-black residue motes, metal shards, dirty-fuchsia edge sparks, dust chips by block material, tiny nail ignition ticks.
- Asset pipeline: recommended sprite sizes, atlas layout, alpha bleeding prevention, pixel-art vs soft VFX constraints, PNG export settings, naming conventions under `assets/jujutsumod`.
- Performance budget: particle counts per phase, lifetimes, spawn bursts, LOD tiers by distance, avoiding allocations in tick/render, avoiding translucent overdraw, profiling approach.
- Multiplayer sync: deterministic seed usage, local derivation, what data server should send, what should never be networked.
- Compatibility risks with Sodium/Iris/resource reload/F3+T.
- Testing/verification checklist: missing texture checks, particle JSON sanity, in-game smoke test, profiling, visual readability pass.

Output format:

1. Executive summary for the project.
2. Exact implementation checklist for Fabric 1.21.8.
3. Hairpin particle taxonomy table with particle name, role, lifetime, texture count, render type, color, spawn phase, LOD behavior.
4. Recommended Java package/resource file structure.
5. Anti-patterns and failure modes.
6. Source trust table: official Fabric/Mojang/Yarn/local-verification vs inspiration-only.
7. Open questions that must be answered before implementation.

Do not give generic Minecraft modding advice. Everything should be specific to Fabric 1.21.8 and this Hairpin production target.

## Prompt 2: Fabric 1.21.8 GLSL, RenderPipeline, Post-Processing, And Shader VFX

Проведи максимально глубокий deep research по GLSL/shader/post-processing/world-space rendering для Minecraft Fabric 1.21.8 / Java 21 / Mojang mappings, specifically for implementing high-end Jujutsu Kaisen-style VFX without breaking Sodium/Iris compatibility.

Контекст проекта: следующий этап после visual target - custom particles, GLSL shaders, screen/world distortion, shock/fracture arcs, possible bloom simulation, outlines, heat haze / refraction for Nobara Hairpin. Hairpin is not a fireball: it is nail-anchored cursed-energy expansion. Visual grammar: `mark -> warn -> compression -> snap -> burst -> residue`. Bloom/residue must be one timeline. Server sends semantic event + seed; client renders transient visual layers.

Исследуй:

- Current Minecraft/Fabric 1.21.8 rendering architecture: extraction vs drawing, render states, immutable extracted state, draw phase boundaries.
- WorldRenderEvents in 1.21.8: correct hooks for transparent world geometry, overlays, before/after translucent terrain, LAST-equivalent behavior, camera-relative transforms.
- RenderPipeline / RenderLayer / VertexConsumer / BufferBuilder / MeshData / RenderPass / MappableRingBuffer concepts and exact API names where verifiable.
- How to render Hairpin world-space geometry: spike meshes, broken fracture arcs, ribbons from nail anchors, shock slashes, screen-facing quads vs 3D geometry.
- GLSL basics in current Minecraft shader asset structure: uniforms, samplers, vertex/fragment stages, included vanilla shader examples, time/seed/intensity uniforms.
- Post-processing options: vanilla post chain, custom framebuffers, Satin-style managed shader effects, what is safe without dependencies, what requires optional dependency or R&D.
- Specific shader effects: radial distortion, chromatic split, blood-black bloom simulation, Sobel/outline-like nail marker, heat haze/refraction, dissolve/residue fade, scrolling UV ribbons, noise-driven fracture.
- Sodium/Iris compatibility: what patterns are safe, what breaks, how to keep standard render layers where possible, avoiding raw OpenGL.
- Accessibility and multiplayer readability: intensity scaling, disabling full-screen effects, observer/victim/attacker profiles.
- Performance and profiling: pass count, framebuffer resolution scaling, fill-rate, translucent sorting, GPU upload costs, how to test.

Output format:

1. Executive summary: what shader stack should be used for the first production Hairpin slice.
2. A conservative MVP shader plan and an advanced optional plan.
3. Architecture diagram/text of data flow from server semantic event to client render state to world/screen passes.
4. Exact file/resource layout for shader assets under `assets/jujutsumod`.
5. API verification checklist for Fabric 1.21.8.
6. Sodium/Iris risk table.
7. Hairpin-specific shader effect recipes with inputs/uniforms/samplers/output behavior.
8. Anti-patterns and no-go zones.
9. Open questions and R&D tasks.

Treat official Fabric/Mojang/Yarn/local-source facts as highest trust. Label examples from NeoForge/Forge/older versions/inspiration clearly.

## Prompt 3: Nobara Hairpin Production VFX Art Bible And Implementation Breakdown

Проведи deep research и production design breakdown именно по Nobara Kugisaki Hairpin/Kanzashi как оригинально вдохновленному, но не копирующему anime assets, Minecraft-native VFX для Fabric 1.21.8.

Контекст проекта: у нас уже есть visual target в `docs/visual-targets/nobara-hairpin/index.html`. Он должен прийти к production-ready дизайну перед созданием кастомных particles/shaders/sounds. Текущая художественная цель: nail-anchored remote detonation; embedded nails are threat markers; phases are `mark -> warn -> compression -> snap -> burst -> residue`; palette is blood-black / black cherry / dark carmine with dirty fuchsia edge and cold metal. Afterglow must not be a separate alien effect; it must be the decay of the same burst vectors. Avoid TNT, fireball, magic smoke sphere, and copied anime frames.

Исследуй:

- Canon/inspiration analysis: what Hairpin means mechanically and visually, how it differs from Resonance, what the nail anchor communicates.
- Translate Hairpin into Minecraft visual language: blocky/cubic constraints, low-poly nails, readable silhouettes, particle density, dark scenes, multiplayer chaos.
- Full VFX storyboard: mark, warning, compression, snap, burst, residue, with timing ranges in ticks/ms and camera/sound notes.
- Asset list: nail models, armed nail material, particle sprites, ribbon textures, spike meshes, residue atlas, UI icons, sound layers, optional shader masks/noise textures.
- Color and material bible: exact palette, ratios, emissive use, metal/straw/fuchsia restraint, shaderless fallback.
- Shape bible: nail marker, embedded surface crack, spike blocks, broken arcs, black-red residue, how not to look like lasers or generic beams.
- Audio bible: metallic ping, hammer crack, cursed hum, dry detonation, gritty tail; mono/stereo rules; distance/LOD behavior.
- Implementation mapping: which parts should be particles, custom world geometry, display/entity/model, HUD overlay, shader pass, sound event.
- Readability and counterplay: what attacker/victim/observer should see/hear, warning fairness, PvP comfort.
- Quality gates: screenshots/clips/checklists to decide "good enough" before porting.

Output format:

1. Production art direction summary.
2. Phase-by-phase storyboard table.
3. Asset manifest with priority, file type, resolution/polycount guidance, and implementation owner.
4. Particle/shader/world-geometry mapping table.
5. Audio event table.
6. Visual anti-patterns and correction strategies.
7. Acceptance checklist for approving the visual target before implementation.
8. Remaining open questions.

Be opinionated. If a design choice is weak, say it directly and propose a stronger one.
