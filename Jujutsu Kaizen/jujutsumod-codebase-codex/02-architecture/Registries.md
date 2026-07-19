# Registries

<- [[00-MOC]] | source prefix: repository root (main branch)

## Items

**Source:** `src/main/java/jujutsu/mod/registry/JujutsuItems.java`

| Field | Path id | Class | Stacks | Line | Status |
|---|---|---|---|---|---|
| `HAIRPIN_NAIL` | `hairpin_nail` | `ProjectJjkNailItem` | 64 | `:19` | VERIFIED |
| `STRAW_DOLL_HAMMER` | `straw_doll_hammer` | `ProjectJjkHammerItem` | 1, durability 256 | `:20` | VERIFIED |
| `PROJECTJJK_HAIRPIN_NAIL` | `projectjjk_hairpin_nail` | `ProjectJjkNailItem` | 64 | `:21` | VERIFIED |
| `PROJECTJJK_STRAW_DOLL_HAMMER` | `projectjjk_straw_doll_hammer` | `ProjectJjkHammerItem` | 1, dur 256 | `:22` | VERIFIED |
| `RESONANCE_REMNANT` | `resonance_remnant` | `ProjectJjkRemnantItem` | 1 | `:23` | VERIFIED |
| `STRAW_DOLL` | `straw_doll` | `ProjectJjkStrawDollItem` | 1 | `:24` | VERIFIED |

Register: `:28-35`. Both "display" and "alias" ids use ProjectJJK behavior classes; the two ritual items are distinct (`:37-55`).

## Data components

**Source:** `src/main/java/jujutsu/mod/registry/JujutsuDataComponents.java`

| Field | Type | Codec | Line | Status |
|---|---|---|---|---|
| `RESONANCE_TARGET` | `DataComponentType<ProjectJjkResonanceRemnant>` | persistent + network | `:11-15` | VERIFIED |
| `RESONANCE_REMNANT_VISUAL` | `DataComponentType<RemnantVisualType>` | persistent + network | `:16-20` | VERIFIED |

Register: `:24-27` as `jujutsumod:resonance_target` and `jujutsumod:resonance_remnant_visual`.

## Mob effects

**Source:** `src/main/java/jujutsu/mod/registry/JujutsuEffects.java`

| Field | Id | Category | Color | Line | Status |
|---|---|---|---|---|---|
| `RESONANT_MOMENTUM` | `jujutsumod:resonant_momentum` | BENEFICIAL | 0x55D6DC | `:11-14` | VERIFIED |

Registration happens via static field initializer (class loading). The `register()` method at `:18-20` triggers class load.

## Entities

**Source:** `JujutsuEntities.java`

| Field | Id | Type | Status |
|---|---|---|---|
| `PROJECTJJK_NAIL` | `projectjjk_nail` | `EntityType<ProjectJjkNailEntity>` | VERIFIED |

## Particles

**Source:** `JujutsuParticles.java`

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

**Source:** `JujutsuSounds.java`

- Hairpin family: `hairpin.prep|hammer_snap|nail_ignite|bloom|afterglow`
- ProjectJJK family: `projectjjk.snap|spell_shot|whoosh_hit|cinematic_whoosh|explode|implode|deep_explosion|black_flash_impact|black_flash_impact2|goo_foley|chime|magic|sizzle|clap|long_whoosh|whoosh_vortex`

**Resource:** `assets/jujutsumod/sounds.json` + `sounds/` (~47 files counted).

## Mixins (client only)

**Source:** `src/client/resources/jujutsumod.client.mixins.json`

| Mixin | Package | Purpose |
|---|---|---|
| `CharacterSkinMixin` | `jujutsu.mod.client.mixin` | replace player skin for Nobara-selected players |
| `HairpinCameraMixin` | same | camera offset from VfxCameraChannel |
| `HairpinGameRendererMixin` | same | FOV offset from VfxCameraChannel |
| `VfxDeltaTrackerMixin` | same | partial-tick time dilation from VfxTimeChannel |
| `NobaraFirstPersonSnapMixin` | same | first-person hand pose from VfxFirstPersonChannel |
| `NobaraLivingEntityRendererMixin` | same | GeckoLib Nobara player model overlay |
| `NobaraPlayerRendererMixin` | same | player render context capture |

`compatibilityLevel: JAVA_21`, `defaultRequire: 1`. No common/server mixins json. **Status:** VERIFIED

---
tags: #jujutsumod #registries #verified
