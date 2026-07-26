# Limitless (Годжо)

← [[_index]]

## Unlock path (`LimitlessClass`)

| Grade | Ability |
|---|---|
| Grade4 | `six_eyes` |
| Grade3 | `infinity` |
| SemiGrade2 | `blue` |
| Grade2 | `red` |
| GradeSpecial | `purple` |

**Not taught by class (registered only):** `blue_mastery`, `red_mastery`, `purple_spark` ⚠

## Registry

| id | dmg | cd | CE | extras |
|---|---:|---:|---:|---|
| infinity | 0 | 5 | 0 | toggle |
| six_eyes | 0 | 35 | 35 | duration **1** min |
| blue | 4 | 8 | 100 | radius **10** |
| red | 15 | 16 | 200 | radius **8** |
| purple | 80 | 40 | **600** | ultimate |
| blue_mastery | 10 | 8 | 100 | maxOut **5**s, dist **5** |
| red_mastery | 15 | 16 | 200 | hold modes |
| purple_spark | 40 | 40 | 600 | hold 2500ms |

## Infinity

- Toggle `ModEffects.INFINITY` duration **1_000_000**
- Effect: only if innate class `"limitless"`
- Visual repel radius ~**2** (+BB pad); skips own projectiles
- Continuous drain **10 CE / second**
- Speech abilities skip infinity players for damage

## Six Eyes

- Apply `SIX_EYES` for 1×60×20 ticks
- Spend refund **50%** CE
- Strips if class ≠ limitless
- Returns extra long **60** (seconds)

## Blue (default)

- Spawn attract at eye+look×**2**
- Timeline ~30 × 50ms ticks
- Attract BB expand `radius+4` (=14); if dist center <4 → `BLUE` damage
- Max attract window ~20 ticks @50ms after 200ms delay
- **Deps:** force CD purple + purple_spark **8s**

## Red (default)

- Charge ~15×50ms at eye+look×2
- `RedExplosion` power 5 + explode(radius 8)
- Living in radius: knockback ×**5**, damage `RED`
- **Deps:** purple CD **16s** (not purple_spark)

## Purple

- Most expensive: 600 CE / 40s CD / 80 dmg
- Multi-stage merge: red t=2, blue t=8, offset 2.0, merge t=12–28 (t^6 ease), idle 8
- Purple entity: moveSpeed 3, maxTicks 100, scale 1.5, explosionRadius 8
- **Note:** damage uses **raw getDamage()** not HP-scaled for main beam (verified decompile)
- Caster: Slowness 80 amp1 then STUN 60 on merge
- **Deps:** red/blue/red_mastery/blue_mastery CD **5s**
- Extra return **5L**

## Blue Mastery hold

| ms | Mode |
|---:|---|
| 0 | none (fail) |
| 500 | default — Blue scale 0.5 forceExplode |
| 1500 | projectile velocity 2.5 |
| 2500 | maximum_output CONTROL follow look dist 5 for 100 ticks @20ms |

Deps purple/purple_spark 8s. Heavy Satin onHold.

## Red Mastery hold

| ms | Mode |
|---:|---|
| 0 | none |
| 500 | default forceExplode |
| 1500 | projectile v=2.5 |
| 2500 | MaximumOutput + raycast 20 for homing UUID |

Deps purple/purple_spark 16s.

## Purple Spark

| ms | Mode |
|---:|---|
| 0 | none (fail) |
| 2500 | shoot purple immediately |

- Damage 40 (half full purple), same 600 CE
- moveSpeed **5**, maxTicks **20**, move true
- scaleFactor 1.4 during hold theater
- Damage cancels hold (ForceCancel packet)
- Deps same as purple 5s

## Damage types

`blue`, `red`, `purple` (+ black_flash/purple bypass armor tags)

---

tags: #projectjjk #limitless #gojo
