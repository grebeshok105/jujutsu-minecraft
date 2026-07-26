# Ratio Technique (Нанами)

← [[_index]]

## Unlock path

| Grade | Ability |
|---|---|
| Grade4 | `ratio` (passive) |
| SemiGrade2 | `swift_strike` |
| SemiGrade1 | `collapse` |
| Grade1 | `overtime` |

## Registry

| id | dmg | cd | CE | extras |
|---|---:|---:|---:|---|
| ratio | 0 | 0 | 0 | crit **10%**, mult **1.75**, passive |
| swift_strike | 5 | 5 | 15 | — |
| collapse | 12 | 5 | 20 | — |
| overtime | 0 | 60 | 30 | survive **20**s, OT **30**s, CD−**75%** |

## Ratio (passive) — VERIFIED proc

- Stores crit chance/multiplier — `Ratio.java:27-34`
- Cast no-op success
- **Proc:** `LivingEntityMixin.ratioPassive` `:365-393`
  - PHYSICAL only + innate class `"ratio"`
  - 10% roll; CD **5s** on ability id `ratio`
  - cancel + re-hurt ×1.75 after 250ms + Satin shake

## Swift Strike

- Hit-scan range **10** / width **1**
- `dashPlayer(10)`
- Damage `SWIFT_STRIKE`
- Brief noclip ~20ms (non-OT)
- **If OVERTIME:** extra VFX + CD reduction return like Collapse

## Collapse

**Normal**

- Raycast **6** / width **1**
- Entity: dmg `COLLAPSE`, knockback look×**2**

**If OVERTIME**

- Delayed **250ms**
- Block hit: ripple 15 + ground entities r**12** take **half** dmg + Y launch 1
- Entity: full dmg, knockback ×**3.5**
- CD return = `−cooldown * (75/100)` → **−3.75** seconds (reduction)

## Overtime (binding vow)

1. Survive countdown **20s** (cancels if dead)
2. Apply `OVERTIME` for **600** ticks (30s)
3. While active: Speed amp **6** every 30 ticks + CE aura entities
4. CD 60s, cost 30 CE
5. Return extra = survival/20 = **20**

During OT, Collapse/SwiftStrike become stronger and refund/reduce CD via reduction %.

---

tags: #projectjjk #ratio #nanami
