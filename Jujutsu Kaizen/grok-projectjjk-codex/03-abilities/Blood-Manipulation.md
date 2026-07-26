# Blood Manipulation (Чосо)

← [[_index]]

## Architecture (critical)

Player inventory shows **holders + control + wing king**, not every sub-skill.

```
blood_control  → mode: hardening | flowing_red_scale | convergence
blood_manipulation (holder)
   ├─ if CONVERGENCE → piercing_blood
   └─ else → slicing_exorcism
blood_utility (holder)
   ├─ if CONVERGENCE → supernova
   └─ else → crimson_binding
wing_king → form
```

## Unlock path (`BloodManipulationClass`)

| Grade | Ability |
|---|---|
| Grade4 | `blood_manipulation` |
| SemiGrade2 | `blood_utility` |
| SemiGrade1 | `blood_control` |
| GradeSpecial | `wing_king` |

Hidden (not taught as inventory abilities):  
`piercing_blood`, `slicing_exorcism`, `supernova`, `crimson_binding`, `flowing_red_scale`, `convergence`, `blood_hardening`

## Registry

| id | dmg | cd | CE | notes |
|---|---:|---:|---:|---|
| blood_manipulation | 0 | 0 | 0 | holder |
| blood_utility | 0 | 0 | 0 | holder |
| blood_control | 0 | 0 | 8 | hold 0 / 1000000 |
| piercing_blood | 4 | 12 | 50 | hold 0/1000/2500 · hidden |
| slicing_exorcism | 12 | 8 | 20 | hold 0/500 · hidden |
| supernova | 10 | 0 | 20 | hidden |
| crimson_binding | 0 | 12 | 30 | hidden |
| wing_king | 0 | 40 | 60 | form 30s |
| flowing_red_scale | 0 | 0 | 0 | mode |
| convergence | 0 | 0 | 0 | mode |
| blood_hardening | 0 | 0 | 0 | mode |

## BloodControl modes

| Type | Meaning |
|---|---|
| BLOOD_HARDENING | default if unknown |
| FLOWING_RED_SCALE | offense mode |
| CONVERGENCE | switches holders to PB / Supernova |
| NONE | no blood_control ability |

UI constants (descriptions):  
- Hardening: −30% dmg / −50% technique dmg taken  
- Flowing Red Scale: +20% damage multiplier text  
- `getBloodDamage`: Hardening → dmg × **0.5**; Flowing → full  

Hold opens popup menu BLOOD_CONTROL; onRelease impact + blood spiral.

## Piercing Blood (via holder when CONVERGENCE)

Constants: maxOutputDuration **60**, bloodRange **25**, singleShotDamagePercent **35**, blockDestroyThreshold **3.0**

| ms | Mode |
|---:|---|
| 0 | none (fail) |
| 1000 | single shot (return 2) after 300ms; dmg formula uses HP×10×60/20×0.35 once |
| 2500 | max output 60 ticks @5ms; every 2 ticks ray dmg; shake 60 |

Ray width 0.2; can grief blocks hardness (0, 3] if mobGriefing.

## Slicing Exorcism (holder when not CONVERGENCE)

| ms | Mode |
|---:|---|
| 0 | none |
| 500 | fire `BloodChakramEntity` maxAge **30** |

OnHold: charge visual maxAge 1200.

## Supernova (holder when CONVERGENCE)

- Needs `bloodOrbStacks` > 0
- **Generation VERIFIED:** `LivingEntityMixin.java:202-230` on BLOOD-modifier damage
  - non-player owner path: 30% (`Supernova.bloodOrbGenerationChance`)
  - max stacks **3** (`new BloodOrbStack(3, ...)`)
  - player path fires `BloodTechniqueUsedEvent` (listener may add stacks too)
- Pop orb, spawn BloodEntity eye+look, vel look×**2**
- `updateBloodOrbs` orbits stacks radius **1**

## Crimson Binding (holder when not CONVERGENCE)

- debuffDuration **100**, debuffStrength **2** (entity applies)
- Spawn CrimsonBindingBloodEntity eye+look×1, vel ×**1.5**, maxAge **40**
- ⚠ Description text may show **45** — mismatch with constants

## Wing King — VERIFIED fire path

- Effect duration **30s** (600 ticks) — ability cast
- Cast applies effect + `SetWingKingRenderPacket`
- **Orb fire:** `LivingEntityMixin.wingKingPassive` `:397-424`
  - when attacker has `WING_KING` effect and not on WingKing CD
  - spawns **8** `HomingBloodEntity` targeting the victim
  - `WingKing.startCooldown(livingEntity)`

## FLOWING_RED_SCALE real mult — VERIFIED

Not only UI text. On physical damage from blood_manipulation player:

**Source:** `LivingEntityMixin.java:164-165` → `newAmount *= 1.2f`

## Blood damage modifier tag

Types with BLOOD modifier: piercing_blood, slicing_exorcism, wing_king — `DamageModifier` init.

---

tags: #projectjjk #blood #choso
