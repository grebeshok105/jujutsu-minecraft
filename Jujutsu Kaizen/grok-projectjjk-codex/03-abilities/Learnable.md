# Learnable Abilities

← [[_index]] · unlocks: [[04-classes/Grade-unlock-tables]]

Common combat skills. Type = `LEARNED`.

## Registry table

| id | dmg | cd(s) | CE | unlock | notes |
|---|---:|---:|---:|---|---|
| power_punch | 3 | 1 | 5 | G4 | ray ~6 / r1 · `POWER_PUNCH` |
| black_flash | 0 | 0 | 0 | **none** | passive — see [[Passives]] |
| pummel_barrage | 1 | 4 | 15 | G4 | **15** punches · `PUMMEL_BARRAGE` |
| uppercut | 1 | 3 | 10 | G4 | launch punch |
| counter | 1 | 8 | 20 | G3 | window; stunDuration **1**s |
| heavy_blow | 4 | 10 | 30 | SG1 | delayed ~200ms; used by Showmaker |
| zenith_focus | 0 | 0 | 0 | SG2 | passive 30% |
| reverse_cursed_technique | 0 | 0 | 0 | GS | toggle RCT effect (1000000 ticks) |
| flash_step | 0 | 0 | 0 | SG2 | short teleport step |
| dash | 0 | 10 | 0 | G4 | `showInInventory=false`; dashPlayer; speed buff |
| finalitys_edge | 1 | 20 | 60 | GS | multi-tick finisher |
| guard | 0 | 3 | 0 | G4 | passive 60% |

## Dash special

- Always cast-check allowed even if inventory rules would block
- Constants: speedBuff amplifier **4**, duration **60** ticks (from class statics)
- Implementation uses `dashPlayer(12)` style movement

## RCT toggle

```
if has REVERSE_CURSED_TECHNIQUE → remove (extra CD 5)
else apply effect duration 1000000
```

Continuous cost 30 CE/tick — [[02-architecture/Cursed-energy]]

## Counter

- Windowed counter ability
- Static `stunDuration = 1` second

## Heavy Blow ↔ Showmaker

Showmaker (Boogie) ends combo by calling `heavy_blow` ability's `onCast`.  
Heavy Blow itself: raycast 5, SCREEN_SHAKE 5, damage type HEAVY_BLOW.

## Pummel Barrage

- totalPunches **15**
- scheduled ticks, radius 1
- IFRAME_BYPASS damage modifier category

## Finality's Edge

- High CE (60), long CD (20s)
- Multi-stage finisher with `FINALITYS_EDGE` damage type

## Power Punch / Uppercut

- Short-range ray/hit melee openers
- Low CE, short CD — bread and butter

---

tags: #projectjjk #learnable
