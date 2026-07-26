# Damage Types

← [[00-MOC]]

## Datapack

`data/projectjjk/damage_type/*.json` — **26** files.

Common fields (verified samples): `exhaustion: 0.1`, `scaling: when_caused_by_living_non_player`, `message_id` = stem.

### Full list

```
black_flash
blast_away
blue
collapse
counter
crumble_away
curse_bind_nail
cursed_energy
dont_move
explode
finalitys_edge
get_twisted
hairpin_enlargement
hairpin_explosion
heavy_blow
nail_damage
piercing_blood
power_punch
pummel_barrage
purple
red
slicing_exorcism
supernova
swift_strike
uppercut
wing_king
```

## ModDamageTypes

Registry keys `projectjjk:<name>`.  
Factories: `of(world, key)`, `of(world, key, attacker)`, `of(world, id)`.

## Categories (`DamageTypeCategories`)

| Category | Examples |
|---|---|
| PHYSICAL | power_punch, counter, heavy_blow, pummel, uppercut, swift_strike… |
| ABILITY | techniques, CE, nails, blue/red/purple… |

**Black Flash only procs on PHYSICAL** category.

## Modifiers (`DamageModifier`)

| Flag | Types |
|---|---|
| IFRAME_BYPASS | pummel_barrage, cursed_energy, finalitys_edge, nail_damage, hairpin_explosion, curse_bind_nail, blue, supernova, piercing_blood |
| BLOOD | piercing_blood, slicing_exorcism, wing_king |

## Vanilla tags (mod additions)

`bypasses_armor`, `bypasses_effects`, `bypasses_enchantments`, `bypasses_resistance`, `bypasses_shield` each include:

- `projectjjk:black_flash`
- `projectjjk:purple`

---

tags: #projectjjk #damage
