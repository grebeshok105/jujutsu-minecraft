# ProjectJJK Nobara Comparison Slice

Status: accepted for implementation from the user request on 2026-07-07.

## Goal

Add a separate ProjectJJK-inspired Nobara slice beside the current `jujutsumod` Nobara slice so both versions can be tested in-game and compared directly.

## Scope

- Keep the current `hairpin_nail` and `straw_doll_hammer` behavior available as the "our" version.
- Add separate ProjectJJK comparison items:
  - `jujutsumod:projectjjk_hairpin_nail`
  - `jujutsumod:projectjjk_straw_doll_hammer`
- Import useful ProjectJJK assets under `assets/jujutsumod/**/projectjjk/...` only after the user stated the ProjectJJK author allowed asset use.
- Do not copy ProjectJJK Java code, mixins, access wideners, bundled jars, shader overrides, or runtime dependencies.
- Do not add GeckoLib in this pass. Keep imported `.geo.json` and animation files as runtime/reference assets, and render the comparison nail through Fabric/Minecraft-native code.
- Keep gameplay server-authoritative.

## Design

The ProjectJJK comparison slice uses real world nail entities. Right-clicking the ProjectJJK nail item spawns held nails in the world in front of the player. The nails stay where they were spawned; they do not recompute from the player's current camera or follow the player.

The ProjectJJK hammer launches prepared nails with a short, violent hammer impulse. Nails fly quickly toward the current target point, use imported ProjectJJK sounds, and detonate on impact with blue cursed-energy flight, burst particles, and a strong local camera/overlay impulse.

The comparison path intentionally stays small:

- Piercing Nail / Hairpin comparison only.
- No Resonance, body parts, doll cinematic, NPC AI, or cursed spirit entity AI in this pass.
- Imported doll/body-part/spirit assets are available for later work but are not activated as gameplay.

## Character Selection Addendum

The comparison build includes a small character selection screen so the player can switch into Nobara while testing the ProjectJJK path. The first implementation is session-only: the server stores the selected character in memory, syncs it to connected clients, and clears it when the player disconnects. Persistent character selection is deferred until the character system needs real progression or saved loadouts.

Nobara's player skin is applied by a narrowly scoped client mixin on `AbstractClientPlayer#getSkin`. A normal Fabric event/layer can add visuals, but it cannot fully replace the vanilla base player skin before the player renderer draws it. The mixin is therefore used only to return a different `PlayerSkin` for players whose synced character selection is `NOBARA`, preventing the default player skin from rendering underneath.

## Acceptance Criteria

- Both Nobara versions are available in the same dev jar through separate items.
- ProjectJJK prepared nails are world-anchored and do not follow the player.
- ProjectJJK hammer hit immediately plays a heavy metal/anvil-like hit, imported ProjectJJK snap/impact sounds, camera shake, and overlay punch.
- ProjectJJK nails fly much faster than the current cinematic row and feel like a strike.
- ProjectJJK nail visuals use the same item/model path as the ProjectJJK nail item.
- Imported ProjectJJK assets are namespaced under `jujutsumod`, with `projectjjk` kept as a resource path segment and no `projectjjk` namespace runtime dependency.
- `gradlew.bat check --no-daemon` and `gradlew.bat build --no-daemon -x test` pass with JDK 21.
