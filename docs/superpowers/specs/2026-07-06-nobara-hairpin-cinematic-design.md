# Nobara Hairpin Cinematic Vertical Slice Design

## Status

Approved direction from the user on 2026-07-06:

- Mod: Fabric `1.21.8`, Java `21`.
- First character: Nobara.
- Canon stance: close to *Jujutsu Kaisen / Магическая битва*.
- First showcase ability: Hairpin.
- Primary focus: absolute cinematic visual quality before full gameplay logic.
- Visual language: stylish shonen impact.
- Development path: VFX bible → standalone visual target → minimal in-game Fabric prototype.
- Custom particles and custom sounds are expected almost everywhere. Vanilla particles/sounds are acceptable only when the difference is not noticeable.

## Goal

Create the first polished visual workflow for the mod by building Nobara's Hairpin as a cinematic vertical slice: a short, high-impact scene that proves the mod can deliver beautiful custom VFX/SFX in Minecraft before the full ability kit and combat balance are implemented.

## Non-Goals

This slice does not need to solve the entire character system.

Out of scope for this first design:

- Full Nobara progression.
- Full PvP/PvE balancing.
- Complete cursed energy economy.
- Full damage formulas.
- Domain Expansion systems.
- Complete ability input framework.
- Final licensed asset pack.
- Large framework abstractions before the first visual target works.

## Product Pillars

1. **Cinema first** — Hairpin should feel staged, sharp, and memorable.
2. **Minecraft-native execution** — the final prototype must run inside Fabric, not only in an external mockup.
3. **Custom audiovisual identity** — custom particles and custom sounds carry the feel; vanilla effects are fallback/support only.
4. **Template potential** — once the Hairpin slice feels right, its workflow becomes the template for future characters.
5. **Small but excellent** — one polished scene beats a half-built roster.

## Design Approach

Use a dual-path workflow.

### Phase 1: VFX Bible

Define Hairpin's visual and audio language before code grows around guesses.

The bible covers:

- Color palette.
- Particle shapes.
- Timing curve.
- Camera/screen impact rules.
- Sound layers.
- Required custom assets.
- What must transfer into Minecraft.

### Phase 2: Standalone Visual Target

Create a small standalone visual target that communicates the scene without being blocked by Minecraft APIs.

Acceptable implementation forms:

- HTML/Canvas or Three.js timing mockup.
- Blender/viewport render if 3D staging matters.
- Short generated reference clip or frame sequence.

The standalone target must show:

- Overall timing.
- Color hierarchy.
- Shape language.
- Impact-frame behavior.
- Particle density.
- Sound-layer notes, even if final audio is not present yet.

### Phase 3: Minimal In-Game Fabric Prototype

Port the approved target into Minecraft as a controlled Hairpin showcase.

The prototype may use a command, debug item, or temporary trigger. Its job is to prove the effect in-game, not to ship final ability UX.

The prototype must include:

- A deterministic trigger.
- A visible Hairpin scene in the world.
- Custom particle types or custom particle textures where needed.
- Custom sound events or temporary placeholder sound events with documented asset needs.
- Clean client/server boundaries.
- Minimal hooks that can later become Nobara's real ability implementation.

## Hairpin Scene

Target duration: **1.5–2.2 seconds**.

The scene is divided into five phases.

### 1. Prep Freeze

The scene starts with a short anticipation beat.

Visuals:

- The target area feels like it momentarily locks in place.
- Thin red-black cursed outlines appear around embedded nails.
- Particles are sparse and precise, not noisy.
- The air around the nails subtly tightens inward.

Audio:

- World audio should feel briefly dampened in the standalone target.
- Use a low, compressed inhale or pressure sound.
- No big impact yet.

Implementation note:

- In Minecraft, do not globally freeze the world for the first prototype.
- Fake the freeze through timing, particle stillness, and sound design.
- Screen/camera effects can be client-side and optional/configurable later.

### 2. Hammer Snap

Nobara's hammer action triggers the technique.

Visuals:

- A sharp snap from the caster direction or trigger point.
- 1–2 frame white or near-white impact flash in the standalone target.
- A small screen kick in the standalone target.
- In-game prototype should approximate this with a short camera/screen accent only if safe and not nauseating.

Audio:

- Metallic hammer crack.
- Short high-frequency transient.
- The sound must be crisp, not boomy.

### 3. Nail Ignition

Each nail becomes a charged node.

Visuals:

- Nails ignite with an alight crimson core.
- Fast tracer lines connect nails to the target point or explosion center.
- Tracers should be clean and directional.
- The player must understand which nails are participating.

Audio:

- Small synchronized ignition ticks.
- Optional rising cursed-energy whine.

Implementation note:

- This phase defines the reusable pattern for multi-point VFX later.
- Store participating nail positions as explicit world positions for the prototype.

### 4. Hairpin Bloom

The charged nails detonate.

Visuals:

- Synchronized flashes.
- Sharp shard-like particles.
- Expanding shock ring.
- Brief high-contrast impact frame.
- Particles should feel like cursed energy tearing outward, not generic fireworks.

Audio:

- Metallic-crack explosion.
- Short layered energy burst.
- Optional low thump under the sharp crack.

Implementation note:

- This is the highest-priority moment of the slice.
- If time is constrained, polish this phase before expanding gameplay logic.

### 5. Afterglow

The scene resolves quickly.

Visuals:

- Red-black sparks decay downward and outward.
- Faint cursed-energy residue remains briefly in air or on the ground.
- The effect should disappear cleanly; no lingering particle spam.

Audio:

- Tiny ember-like particles or glassy tail.
- No long cinematic swell.

Implementation note:

- Particle lifetime must be controlled tightly for performance.

## Visual Language

Primary direction: **stylish shonen impact**.

### Palette

Core colors:

- Crimson energy: `#D7193F`
- Deep curse red: `#650012`
- Black shadow accents: `#080206`
- White impact flash: `#F8F2EA`
- Hot pink highlight: `#FF4D7D`

Usage:

- Crimson is the main read.
- Deep red/black create cursed contrast.
- White appears only on impact frames or very hot highlights.
- Hot pink is rare and used for high-energy edges.

### Shape Language

- Nails: thin, directional, metallic, precise.
- Energy: angular streaks, sharp arcs, short-lived blooms.
- Shockwaves: clean expanding rings, not smoky clouds.
- Residue: small sparks and thin fading trails.

Avoid:

- Generic explosion smoke as the main read.
- Rainbow particles.
- Slow magical swirls.
- Overly soft wizard-like glows.

## Audio Language

Custom sounds are part of the core identity.

Hairpin needs at least these sound layers:

1. **Pressure inhale** — quiet anticipation before the snap.
2. **Hammer crack** — crisp metallic trigger.
3. **Nail ignition ticks** — small fast charge sounds.
4. **Hairpin burst** — sharp metallic-energy detonation.
5. **Afterglow tail** — short spark decay.

Asset request for the user:

- metallic hammer hit / crack
- sharp nail ping / ignition ticks
- short energy burst / slash-like transient
- subtle cursed-energy rise
- short spark/glass tail

Runtime audio must be OGG Vorbis.

## First In-Game Prototype Shape

The first Fabric prototype should be deliberately small.

Suggested trigger options, in priority order:

1. Debug command: `/jujutsu hairpin`
2. Temporary debug item.
3. Temporary keybind.

Recommended first trigger: **debug command**, because it avoids designing the full input system too early.

The command should spawn/play the Hairpin scene around a controlled target point:

- easiest mode: effect at the block/entity the player is looking at;
- fallback mode: effect a few blocks in front of the player;
- later mode: effect on previously embedded nail positions.

## Client/Server Boundary

Server responsibilities:

- Authorize the debug trigger.
- Choose world position(s) for the effect.
- Broadcast effect event to nearby clients.
- Later: own damage, cooldown, cursed energy cost, and target validity.

Client responsibilities:

- Play particles.
- Play local screen/camera accents.
- Play non-authoritative visual timing.
- Render temporary visual-only traces/rings.

Do not put gameplay authority in client code.

## Dependency Direction

Start with Fabric APIs and custom project code.

Do not make Veil, Lodestone, GeckoLib, or any other VFX/animation library mandatory until a specific need is proven.

Current research notes:

- GeckoLib appears useful for future animated items/entities/armor and has active Fabric 1.21.x support.
- Veil is powerful for advanced rendering, but support must be verified for Minecraft 1.21.8 before it becomes a dependency.
- Lodestone is risky as a first dependency because some ecosystem users have moved away from it toward Veil.

Decision:

- First spec: no mandatory heavy VFX framework.
- GeckoLib may be evaluated later for item/character animation.
- Veil may be evaluated later as optional advanced rendering support if Fabric particles are insufficient.

## Universal FX / LibsFX Decision

The old custom FX library was found at `D:/WorkFlow old/WorkFLow/TestimCodex/LibsFX`.

It is named **Universal FX Library** (`universal_fx`, Maven `universal.fx:universal_fx:1.0.0`). It currently targets:

- Minecraft `1.21.1`
- Fabric API `0.116.12+1.21.1`
- Loom `1.17.11`
- Satin `2.0.0`

The Jujutsu mod targets Minecraft `1.21.8`, so Universal FX must **not** be used as a required dependency for the first Hairpin slice.

Decision:

- Do not block Nobara/Hairpin on porting Universal FX.
- Do not downgrade the Jujutsu mod to `1.21.1` for this library.
- Use Universal FX only as a reference for ideas, API shape, catalogs, recipes, and renderer organization.
- Port Universal FX later only if the first pure-Fabric Hairpin prototype proves that the missing pieces are worth the porting cost.

Reasoning:

- The library is not verified on Minecraft `1.21.8`.
- A fresh build attempt failed before compilation because Satin `2.0.0` could not be resolved from Ladysnake Maven.
- Even if the dependency download is fixed, the version gap still requires a real port and runtime smoke test.
- The first milestone needs visual proof fast, not a long library rescue mission.

## Files Expected Later

This design does not require all files to exist immediately. They are likely implementation targets once planning starts.

Possible future structure:

- `src/main/java/jujutsu/mod/command/` — debug trigger command.
- `src/main/java/jujutsu/mod/network/` — effect payloads.
- `src/client/java/jujutsu/mod/client/fx/` — Hairpin visual playback.
- `src/client/java/jujutsu/mod/client/particle/` — custom particle factories.
- `src/main/resources/assets/jujutsumod/particles/` — particle definitions/textures.
- `src/main/resources/assets/jujutsumod/sounds.json` — sound event definitions.
- `art-source/` — source/reference assets if external/custom assets are added.

Do not create empty packages before implementation needs them.

## Acceptance Criteria

The first completed Hairpin vertical slice is acceptable when:

1. The repository contains a VFX bible section or document for Hairpin.
2. A standalone visual target demonstrates the five-phase timing.
3. The in-game Fabric prototype can trigger the Hairpin scene deterministically.
4. The prototype uses custom particle visuals for the main read.
5. The prototype uses custom sound events or documented temporary placeholders.
6. Client/server boundaries are respected.
7. The visual effect is recognizable as Hairpin without needing a full combat system.
8. The prototype compiles on Java 21 with Minecraft `1.21.8` and Fabric.
9. The implementation leaves a clear path to add Nobara's full kit later.

## Verification Plan

For design/spec changes:

- Review this file for placeholders, contradictions, and ambiguous scope.
- Commit the spec.

For implementation later:

- Run `cmd.exe /c gradlew.bat build --no-daemon -x test` with Java 21.
- Run in-game smoke testing once the prototype exists.
- Compilation alone is not enough to claim the Hairpin scene feels correct.

## Open Asset Needs

The user offered to find custom assets. The first concrete requests are:

- Short hammer metallic crack.
- Nail ping / nail ignition ticks.
- Sharp cursed-energy burst.
- Subtle charge/pressure rise.
- Short spark decay tail.
- Small custom particle textures:
  - angular crimson spark;
  - thin cursed tracer;
  - bright impact shard;
  - soft but short-lived crimson core;
  - shock-ring texture or ring rendering approach.

## Next Step

After the user reviews and approves this spec, create an implementation plan. The plan should first build the VFX bible/standalone target, then the Fabric Hairpin prototype.
