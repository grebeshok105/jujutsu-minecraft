# Assets & Resources

← [[00-MOC]] · prefix cinematic worktree

## Root

`src/main/resources/assets/jujutsumod/`

## Inventory (counted 2026-07-08)

| Area | Count | Status |
|---|---:|---|
| particles JSON | 8 | VERIFIED |
| sounds (files) | ~47 | VERIFIED |
| items JSON | 4 | VERIFIED |
| models/item | 4 | VERIFIED |
| textures (recursive) | ~356 | VERIFIED |
| shaders | 8 | VERIFIED |
| lang `en_us.json` keys | 38 | VERIFIED |

## Critical resource maps

| Runtime need | Resource |
|---|---|
| Particle types | `particles/*.json` + `textures/particle/**` |
| Sounds | `sounds.json` + `sounds/hairpin/*` + `sounds/projectjjk/*` |
| Items | `items/*.json` + `models/item/*` |
| Shaders | `shaders/include/*`, `shaders/post/*` |
| Icon | `icon.png` |
| ProjectJJK comparison art | `geo/projectjjk`, `animations/projectjjk`, textures under `projectjjk/` |

## Lang families

- `item.jujutsumod.*` nails/hammers (+ ProjectJJK alias labels)
- `key.jujutsumod.character_select` (V key category)
- `screen.jujutsumod.character_select.*`
- `message.jujutsumod.projectjjk.*`
- `subtitles.jujutsumod.*`

**Source:** `assets/jujutsumod/lang/en_us.json`  
**Status:** VERIFIED

## Legal note

ProjectJJK-derived assets under `projectjjk/` paths are comparison/research material — follow project import notes; ProjectJJK jar is ARR.

---
tags: #jujutsumod #assets
