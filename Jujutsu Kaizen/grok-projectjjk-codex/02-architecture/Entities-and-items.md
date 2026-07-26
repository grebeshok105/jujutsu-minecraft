# Entities & Items

← [[00-MOC]] · Straw Doll detail: [[03-abilities/Straw-Doll]]

## Critical: no hammer/nail tools in ProjectJJK inventory

Nobara kit is **abilities + entities**, not craftable nail/hammer items.  
(Your jujutsumod ProjectJJK comparison kit **does** add items — that's your reimpl, not upstream.)

## Items (registered)

| Item id | Role |
|---|---|
| `nobara_kugisaki_spawn_egg` | combat NPC |
| `cursed_relic_of_affinity` | class selection |
| `jujutsu_promotion_letter` | progression |
| `cursed_key` | dungeon/door |
| `ritual_block` | GeoItem block |
| various spirit/NPC eggs | PvE |

~9 item classes under `net.hadences.item`.

## NailEntity

Path: `entity/custom/projectile/NailEntity.java`

- Extends projectile, implements GeoEntity
- maxAge default **1200** ticks
- On entity hit: `ModDamageTypes.NAIL_DAMAGE` with owner attacker
- Skips owner; not player-pickupable
- Spawned by PiercingNail hold modes

## BodyPartEntity / DollEntity

- Body parts: fuel for Resonance (owner UUID)
- DollEntity: VFX prop during Resonance sequence

## Limitless entities

- LimitlessBlueEntity / LimitlessRedEntity (simple orbs)
- BlueEntity / RedEntity (defaults: duration 20, radius 6, maxAge 100)
- LimitlessPurpleEntity (beam/projectile)

## Blood entities

- BloodEntity, BloodChakramEntity, HomingBloodEntity, CrimsonBindingBloodEntity
- WingKingEntity / CEAuraEntity

## VFX entities (Geo + display)

Base: `VFXEntity` — plane/laser models, scale, color, spin.

Examples:

- Absorb32, BFLightning, BloodFlare/Flash/Spiral/Splash
- CEAura, CursedEnergy, Flash32/64, FlashStrike64/2
- Impact64, LaserBeam, RayAbsorb/Circle/Repel, ImpactSlash, Spark, Vortex

## Spirits / NPCs

Cursed spirits grades 4 → special (cow/pig/sheep, grade1–3, special).  
`NobaraKugisakiEntity` — SmartBrainLib combos using straw doll movesets + learnable punches.  
Attributes at reg (from ProjectJJK init refs): HP ~45, attack ~3.

## Movesets

`entity/movesets/cursed_techniques/*` — AI choreography mirrors player abilities.  
Player numbers live primarily in ability classes; movesets for NPC timing.

---

tags: #projectjjk #entities #items
