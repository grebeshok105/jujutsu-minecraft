# Straw Doll Technique (Нобара)

← [[_index]] · class: [[04-classes/Class-selection]] · citations: [[05-reference/Claim-Source-Index]]

## Fantasy

Гвозди → glow-tag / body part → hairpin (enlarge/explode/bind) → resonance через куклу.

## Primary sources (open these first)

| Topic | Path |
|---|---|
| Registry puts | `AbilityRegistry.java:172-177` |
| Unlock grades | `class_selection/types/StrawDollClass.java:26-40` |
| Nail holds | `.../PiercingNail.java:53-55` |
| Nail → glow tag | `mixin/LivingEntityMixin.java:234-318` |
| Body part drop | `LivingEntityMixin.java:343-361` |
| Resonance cast | `.../Resonance.java` |
| ITE tags | `util/ITEVisualizer.java` |

## Unlock path (`StrawDollClass`) — VERIFIED

| Grade | Abilities | Source |
|---|---|---|
| Grade4 | `resonant_remains`, `piercing_nail` | `StrawDollClass.java:26-27` |
| Grade3 | `nail_bind_curse` | grade3 method |
| SemiGrade2 | — | empty |
| Grade2 | `hairpin_explosion` | grade2 |
| SemiGrade1 | `hairpin_enlargement` | semi1 |
| Grade1 | `resonance` | grade1 |
| GradeSpecial | — | empty |

## Registry numbers — VERIFIED (`AbilityRegistry.java:172-177`)

| id | dmg | cd | CE | extras | passive |
|---|---:|---:|---:|---|---|
| piercing_nail | 0.5 | 1 | 5 | speed **1.0** | no |
| nail_bind_curse | 0.5 | 10 | 40 | range **10** | no |
| hairpin_explosion | 1 | 12 | 30 | range **10** | no |
| hairpin_enlargement | 12 | 15 | 15 | range **20** | no |
| resonance | 20 | 20 | 100 | suppress **6**s | no |
| resonant_remains | 0 | 10 | 35 | drop chance **5%** | **yes** |

---

## Piercing Nail

**Hold (ms)** — `PiercingNail.java:53-55`:

| ms | Mode | Behavior |
|---:|---|---|
| 0 | nail_shot | 1 nail at eye+look; after **100ms** velocity = look × speed 1.0 |
| 300 | triple_nails | 3 nails left/center/right; after 100ms; yaw ±15° |
| 800 | nail_barrage | **10** nails every **20ms**; speed = base + rand*0.4; returns extra CD `2` |

- Entity: `NailEntity` (maxAge 1200) — `entity/custom/projectile/NailEntity.java`
- Hit damage type: `nail_damage`
- On hit (server): `LivingEntityMixin.java:234-238` → `onNailDamage`
- **Glow tag:** create/update `ITEVisualizer` color `2943221`, distance `20`, tag entity **600** ticks (`:310-318`)
- No ability deps

## Nail Bind Curse (lang: Shadow Impale)

- Detect owned nails: hit-scan eye+look×4, range **10**, width **5**
- Start after **250ms**, every **20ms** process 1–2 nails
- AoE expand **1.5**, damage `curse_bind_nail`
- Stun: player **2s** / mob STUN **20** ticks amp 1
- VFX: FlashStrike64VFX2
- Sound: SNAP

## Hairpin Explosion

- Same nail scan as bind (range 10, width 5)
- Shuffle nails; mark energy active
- After **500ms**, every **20ms** explode 1–2 nails
- AoE **1.5**, knockback-ish launch **0.2**
- Damage `hairpin_explosion`
- Satin SCREEN_SHAKE caster 5 / hit players 10
- Flash32VFX

## Hairpin Enlargement

- Raycast range **20**, width **0.2**
- Target must be living **and** in player's `ITEVisualizer` tags
- Fail message: `hairpin_enlargement.error.no_entity`
- FP anim `"snap"`
- After **1000ms**: damage `hairpin_enlargement`, stun player 2s / mob STUN **50** ticks amp 1, remove tag
- Satin: SHAKE 8, B&W 4

## Resonance

- Cost **100** CE (expensive)
- Hit-scan range **3.0**, width **0.2** for `BodyPartEntity`
- Spawn `DollEntity` at part
- After **750ms** impact → **50ms** later: destroy body part
- If owner UUID of part exists within BB expand **+30**: damage `nail_damage` + apply `SUPPRESSED` for **120** ticks (6s)
- Doll despawn **1000ms**
- Satin: B&W 4, SHAKE 10

## Resonant Remains (passive) — VERIFIED drop path

- Chance field **5%** — `ResonantRemains.java:29-33`
- Cast returns false (passive)
- **Drop path:** `LivingEntityMixin.strawDollPassive` `:343-361`
  - on damage if attacker has ability
  - `random(100) < 5` and not on CD
  - `startCooldown("resonant_remains", 10s)`
  - spawn `BodyPartEntity` at victim

## Damage types used

**extract:** `data/projectjjk/damage_type/{nail_damage,hairpin_enlargement,hairpin_explosion,curse_bind_nail}.json`

## Loop diagram (verified hooks)

```
PiercingNail → NailEntity hit
     │
     ├─ onNailDamage → ITEVisualizer tag 600t (enlarge gate)
     ├─ HairpinExplosion  (owned nails cone)
     ├─ NailBindCurse     (owned nails return AoE)
     └─ HairpinEnlargement (requires ITE tag)
            │
strawDollPassive 5% → BodyPartEntity (on damage, CD 10s)
            │
        Resonance (ray BodyPart) → remote owner dmg + SUPPRESSED 6s
```

## Vs jujutsumod ProjectJJK kit

Your reimpl adds **items** (nail/hammer), marks manager, detonate/enlarge/resonance without full CE economy.  
Upstream uses ability hotbar + CE + body parts + ITE tags.  
Numbers differ — treat as reference, not copy-paste.

---

tags: #projectjjk #nobara #straw-doll
