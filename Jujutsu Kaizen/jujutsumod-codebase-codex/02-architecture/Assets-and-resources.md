# Assets & Resources

<- [[00-MOC]] | prefix: repository root (main branch)

## Root

`src/main/resources/assets/jujutsumod/`

## Inventory

| Area | Count | Status |
|---|---:|---|
| particles JSON | 8 | VERIFIED |
| sounds (files) | ~47 | VERIFIED |
| items JSON | 6 | VERIFIED |
| models/item | 5 conventional JSON + 1 GeckoLib special item definition | VERIFIED |
| textures (recursive) | ~356 | VERIFIED |
| original Straw Doll geo/animation source set | 1 geo + 1 animation + 1 `.bbmodel` + generators/previews | VERIFIED |
| lang | `en_us.json` + `ru_ru.json` | VERIFIED |

## Critical resource maps

| Runtime need | Resource |
|---|---|
| Particle types | `particles/*.json` + `textures/particle/**` |
| Sounds | `sounds.json` + `sounds/hairpin/*` + `sounds/projectjjk/*` |
| Conventional items | `items/*.json` + `models/item/*` |
| Animated Straw Doll | `items/straw_doll.json` + `geckolib/models/straw_doll.geo.json` + `geckolib/animations/straw_doll.animation.json` + `textures/item/straw_doll.png` |
| Editable original asset source | `source-assets/blockbench/straw_doll.bbmodel` + deterministic texture/preview scripts |
| Icon | `icon.png` |
| ProjectJJK comparison art | `geo/projectjjk`, `animations/projectjjk`, textures under `projectjjk/` |

## Lang families

- `item.jujutsumod.*` nails/hammers, Straw Doll, and bound remnant (+ ProjectJJK alias labels)
- `key.jujutsumod.character_select` (V key category)
- `screen.jujutsumod.character_select.*`
- `message.jujutsumod.projectjjk.*`
- `subtitles.jujutsumod.*`

**Source:** `assets/jujutsumod/lang/en_us.json`, `assets/jujutsumod/lang/ru_ru.json`
**Status:** VERIFIED

## Legal note

ProjectJJK-derived assets under remaining `projectjjk/` paths are comparison/research material - follow project import notes; ProjectJJK jar is ARR. The copied ProjectJJK doll geometry, animation, and texture were removed from the runtime namespace and are guarded as absent.

The runtime `straw_doll` assets and their `source-assets/blockbench` source are original project work. They deliberately avoid ProjectJJK's copied box proportions, textures, and animation keyframes. Live Blockbench inspection on 2026-07-10 confirmed texture resolution, 25-element source/runtime parity, zero model issues, and editable keyframes for all four clips.

---
tags: #jujutsumod #assets
