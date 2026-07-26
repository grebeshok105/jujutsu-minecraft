# Hold Thresholds (ms)

← [[00-MOC]] · unit verified by magnitude + PiercingNail comments pattern

At cast, highest threshold ≤ `heldDuration` wins.

| Ability | Thresholds (ms) | Modes (low → high) |
|---|---|---|
| piercing_nail | 0 / 300 / 800 | shot / triple / barrage |
| blue_mastery | 0 / 500 / 1500 / 2500 | none / default / projectile / max |
| red_mastery | 0 / 500 / 1500 / 2500 | none / default / projectile / max |
| purple_spark | 0 / 2500 | none / purple |
| piercing_blood | 0 / 1000 / 2500 | none / single / max |
| slicing_exorcism | 0 / 500 | none / cast |
| blood_control | 0 / 1000000 | toggle / placeholder |

All other abilities: only `IntervalThreshold(0)`.

## UI language keys (examples)

- `projectjjk.ability.piercing_nail.hold_function.nail_shot|triple_nails|nail_barrage`
- `projectjjk.ability.blue_mastery.hold_function.default|projectile|maximum_output`
- `projectjjk.ability.purple_spark.hold_function.purple`

---

tags: #projectjjk #reference #hold
