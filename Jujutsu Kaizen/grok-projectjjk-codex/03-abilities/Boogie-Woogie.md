# Boogie Woogie (Тодо)

← [[_index]]

## Unlock path

| Grade | Ability |
|---|---|
| Grade4 | `spatial_swap` |
| Grade3 | `phantom_applause` |
| Grade2 | `displacement_burst` |
| SemiGrade1 | `showmaker` |
| GradeSpecial | `sixth_sense` |

## Registry

| id | dmg | cd | CE | extras |
|---|---:|---:|---:|---|
| spatial_swap | 0 | 1 | 25 | range **30** |
| displacement_burst | 0 | 1 | 80 | entityRadius **40** (desc), maxDist **10** |
| sixth_sense | 0 | 35 | 35 | **1** min, **25%** |
| showmaker | 12 | 12 | 35 | range **10**, height **10** |
| phantom_applause | 0 | 20 | 35 | timer **15**s, range **10** |

## Spatial Swap

- Clap → raycast 30 / width 0.5
- Swap positions with entity
- Entity: Levitation **30** ticks amp 1
- Player swap delayed **20ms**
- No damage

## Displacement Burst

- ⚠ `entityRadius=40` only in description text
- Cast uses raycast **maxDistance=10** + nearby BB expand **hardcoded 10**
- Needs two entities (target + partner); error if missing
- Swap after **20ms**; Levitation 30 on living target
- Filters creative/spectator quirks

## Sixth Sense — VERIFIED proc

- Apply `SIXTH_SENSE` for 1×60×20 ticks — `SixthSense.java:61`
- Return extra **60**
- Chance field **25** — registry `:169` / getter
- **Proc:** `PlayerEntityMixin.sixthSensePassive` `:234+`
  - roll `random(100) < getSixthSenseChance()`
  - on PHYSICAL (or player-attack tag — re-read full condition `:243`)
  - **cancels** incoming damage and counter-positions player relative to attacker

## Showmaker

1. Raycast range 10
2. If vertical air column of height `teleportHeight` clear: teleport target up by height−2
3. Levitation amp **100** for 20 ticks
4. **50ms** later player teleports above target
5. **250ms** later invokes learnable **`heavy_blow.onCast`**
6. Poll 15×20ms until target grounded → STUN 20, shockwave, AoE BB+**5** Showmaker dmg + launch Y=1

Not a registry dependency — direct ability call.

## Phantom Applause

- Apply effect for 15×20 ticks
- Effect: every second **25%** chance clap + Levitation 30; non-players also STUN 30
- Return extra **15**

---

tags: #projectjjk #boogie-woogie
