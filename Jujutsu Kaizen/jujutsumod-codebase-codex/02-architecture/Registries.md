# Registries

← [[00-MOC]] · prefix `.worktrees/nobara-cinematic-slice/`

## Items

**Source:** `src/main/java/jujutsu/mod/registry/JujutsuItems.java`

| Field | Path id | Class | Stacks | Source | Status |
|---|---|---|---|---|---|
| `HAIRPIN_NAIL` | `hairpin_nail` | `ProjectJjkNailItem` | 64 | `:14` | VERIFIED |
| `STRAW_DOLL_HAMMER` | `straw_doll_hammer` | `ProjectJjkHammerItem` | 1, durability 256 | `:15` | VERIFIED |
| `PROJECTJJK_HAIRPIN_NAIL` | `projectjjk_hairpin_nail` | `ProjectJjkNailItem` | 64 | `:16` | VERIFIED |
| `PROJECTJJK_STRAW_DOLL_HAMMER` | `projectjjk_straw_doll_hammer` | `ProjectJjkHammerItem` | 1, dur 256 | `:17` | VERIFIED |
| `RESONANCE_REMNANT` | `resonance_remnant` | `ProjectJjkRemnantItem` | 1 | `:18` | VERIFIED |
| `STRAW_DOLL` | `straw_doll` | `ProjectJjkStrawDollItem` | 1 | `:19` | VERIFIED |

Register: `:23-30`. Both “display” and “alias” ids use ProjectJJK behavior classes; the two ritual items are distinct (`:32-50`).

## Data components

`JujutsuDataComponents.RESONANCE_TARGET` stores `ProjectJjkResonanceRemnant(targetId, dimension, targetName)`. It has both a persistent codec and network stream codec and registers as `jujutsumod:resonance_target` (`JujutsuDataComponents.java:10-20`).

Resources: `assets/jujutsumod/items/*.json`, `models/item/*.json`, lang keys `item.jujutsumod.*`.

## Entities

**Source:** `JujutsuEntities.java:13`

| Field | Id | Type | Status |
|---|---|---|---|
| `PROJECTJJK_NAIL` | `projectjjk_nail` | `EntityType<ProjectJjkNailEntity>` | VERIFIED |

## Particles

**Source:** `JujutsuParticles.java:10-17`

| Constant | Typical path id |
|---|---|
| `HAIRPIN_SPARK` | hairpin_spark |
| `HAIRPIN_MARK_STAIN` | hairpin_mark_stain |
| `HAIRPIN_WARN_EDGE` | hairpin_warn_edge |
| `HAIRPIN_COMPRESSION_MOTE` | hairpin_compression_mote |
| `HAIRPIN_SNAP_CRACK` | hairpin_snap_crack |
| `HAIRPIN_BURST_RESIDUE` | hairpin_burst_residue |
| `HAIRPIN_BURST_METAL_SHARD` | hairpin_burst_metal_shard |
| `HAIRPIN_IGNITION_TICK` | hairpin_ignition_tick (registered compatibility asset; no longer used by Nobara runtime/recipes) |

**Resource:** 8 JSON under `assets/jujutsumod/particles/`.  
Client factories: `JujutsuClientParticles`.

## Sounds

**Source:** `JujutsuSounds.java:9-29`

- Hairpin family: `hairpin.prep|hammer_snap|nail_ignite|bloom|afterglow`
- ProjectJJK family: `projectjjk.snap|spell_shot|whoosh_hit|cinematic_whoosh|explode|implode|deep_explosion|black_flash_impact|black_flash_impact2|goo_foley|chime|magic|sizzle|clap|long_whoosh|whoosh_vortex`

**Resource:** `assets/jujutsumod/sounds.json` + `sounds/` (~47 files counted).

## Mixins (client only)

**Source:** `src/main/resources/fabric.mod.json:21-25` + `src/client/resources/jujutsumod.client.mixins.json`

| Mixin | Package |
|---|---|
| CharacterSkinMixin | `jujutsu.mod.client.mixin` |
| HairpinCameraMixin | same |
| HairpinGameRendererMixin | same |

No common/server mixins json. **Status:** VERIFIED

---
tags: #jujutsumod #registries
