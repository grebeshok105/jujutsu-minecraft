# Ability Dependencies

← [[00-MOC]] · mechanism: `addDependentAbility(id, seconds)` → `forceHighestCooldown`

## Cursed Speech mutual lock

| Caster | Forces CD on | Seconds |
|---|---|---:|
| crumble_away | blast_away, dont_move, explode, get_twisted | 1 |
| blast_away | dont_move, explode, get_twisted, crumble_away | 1 |
| explode | blast_away, dont_move, get_twisted, crumble_away | 2 |
| dont_move | blast_away, explode, get_twisted, crumble_away | 2 |
| get_twisted | blast_away, dont_move, explode, crumble_away | 2 |

## Limitless locks

| Caster | Target | Seconds |
|---|---|---:|
| blue | purple, purple_spark | 8 |
| blue_mastery | purple, purple_spark | 8 |
| red | purple | 16 |
| red_mastery | purple, purple_spark | 16 |
| purple | red, blue, red_mastery, blue_mastery | 5 |
| purple_spark | red, blue, red_mastery, blue_mastery | 5 |

Note: default **red** does **not** lock purple_spark (only purple). Mastery red does lock purple_spark.

## Soft dependencies (not map)

| Caster | Calls |
|---|---|
| showmaker | `heavy_blow.onCast` after setup |
| blood holders | delegate cast to child abilities |
| hairpin_* | require owned nails / ITE tags / body parts |

---

tags: #projectjjk #reference #dependencies
