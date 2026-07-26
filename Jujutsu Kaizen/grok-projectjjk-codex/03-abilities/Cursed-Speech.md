# Cursed Speech (Инумаки)

← [[_index]] · deps: [[05-reference/Dependencies]]

## Unlock path

| Grade | Ability |
|---|---|
| Grade4 | `get_twisted`, `blast_away` |
| Grade3 | `explode` |
| SemiGrade2 | `dont_move` |
| Grade2 | `crumble_away` |

## Shared cone

- Origin: eye + look × **4**
- Distance **15**, radius **5**
- Sound: `CURSED_SPEECH_SCREECH`
- Infinity players: **skip damage** (most spells)
- Satin SCREEN_SHAKE 10 common

## Registry + behavior

| id | dmg | cd | CE | deps (s) | Behavior |
|---|---:|---:|---:|---|---|
| get_twisted | 8 | 15 | 20 | others **2** | after 100ms: dmg GET_TWISTED, Slowness 40 amp4, Darkness 10 amp3 |
| blast_away | 3 | 15 | 50 | others **1** | after 100ms: dmg BLAST_AWAY, knockback dir×5 (Y 0.2) |
| explode | 5 | 20 | 80 | others **2** | after 50ms: dmg EXPLODE, BlockOnlyExplosion r3 p2 |
| dont_move | 0 | 30 | 100 | others **2** | stun **3**s; dmg type DONT_MOVE; player stunPlayer / mob STUN |
| crumble_away | 15 | 25 | 100 | others **1** | dmg CRUMBLE_AWAY, vel Y−5, ground search 10 down, CylinderExplosion (5,4,2) p5 |

## Design notes

- Kit is mutual soft-lock: casting one short-CDs siblings so you cannot spam every word
- Crumble is the heavy grade-2 finisher
- DontMove is pure CC (0 base dmg) at high CE cost

---

tags: #projectjjk #cursed-speech
