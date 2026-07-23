# Nobara Item 3D Model Technique

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Status: superseded archival note.

Superseded 2026-07-09: the default `hairpin_nail` and `straw_doll_hammer` ids now point at ProjectJJK item models and textures. The old `textures/item/hairpin_nail.png`, `textures/item/straw_doll_hammer.png`, and `textures/item/model/*` assets described below were removed with the legacy Nobara cleanup. Keep this note only as historical context for why current item models must sample opaque texture regions.

## Purpose

The `hairpin_nail` and `straw_doll_hammer` items use Minecraft JSON item models to look like simple 3D props in hand and in the world while still keeping readable inventory icons. This became the first reusable pattern for small character equipment before we move into custom entity/render-layer work.

## The Important Lesson

Do not map 3D cuboid faces directly to sparse transparent GUI sprites.

The first model pass reused `textures/item/hairpin_nail.png` and `textures/item/straw_doll_hammer.png` for cuboid faces. Those icon sprites were mostly transparent, which is fine for a flat inventory item, but cuboid face UVs sampled transparent pixels. In game this looked like ripped, broken, missing model surfaces.

The fixed pattern separates two texture roles:

- GUI/reference sprite:
  - `src/main/resources/assets/jujutsumod/textures/item/hairpin_nail.png`
  - `src/main/resources/assets/jujutsumod/textures/item/straw_doll_hammer.png`
- Opaque 3D surface tiles:
  - `src/main/resources/assets/jujutsumod/textures/item/model/dark_steel.png`
  - `src/main/resources/assets/jujutsumod/textures/item/model/steel_edge.png`
  - `src/main/resources/assets/jujutsumod/textures/item/model/oxblood.png`
  - `src/main/resources/assets/jujutsumod/textures/item/model/hammer_wood.png`

The model JSON keeps `layer0` as the item sprite, but every cuboid face uses the opaque model textures:

```json
"textures": {
  "layer0": "jujutsumod:item/hairpin_nail",
  "dark_steel": "jujutsumod:item/model/dark_steel"
},
"faces": {
  "north": {"texture": "#dark_steel", "uv": [0, 0, 16, 16]}
}
```

## Model Shape

The effect is intentionally Minecraft-native:

- A few cuboids create the silhouette.
- Rotation on each cuboid gives the prop a held/action angle.
- Extra small cuboids add identity: nail head, point, oxblood band, hammer wrap, hammer head caps.
- `display` transforms tune first person, third person, ground, and GUI views separately.

This is cheap, resource-pack friendly, and does not require a custom renderer.

## Regression Guard

`src/test/java/jujutsu/mod/ProjectSanityTest.java` now validates:

- item definitions resolve to model JSON;
- every referenced `jujutsumod:item/...` texture exists;
- every model face has a texture and UV;
- sampled face UV regions are at least 90% opaque.

This catches the exact broken-surface failure mode before a jar is handed off.

## Reuse Checklist

For future character equipment:

1. Draw the icon as a readable item sprite.
2. Create separate opaque `textures/item/model/...` tiles for 3D faces.
3. Build the item as a small number of cuboids in `models/item/<item>.json`.
4. Use full tile UVs (`[0, 0, 16, 16]`) unless there is a reason to crop.
5. Keep transparent pixels out of textures used by cuboid faces.
6. Run `testProjectSanity` before handing off the jar.

Use this pattern for small tools, talismans, nails, charms, handles, wraps, and other equipment that should read as 3D without needing animation bones or custom render code.
