# Rank & Progression

← [[00-MOC]] · [[Cursed-energy]] · [[04-classes/Grade-unlock-tables]]

## Rank ladder

Same naming as ability grades:

1. GRADE_4  
2. GRADE_3  
3. SEMI_GRADE_2  
4. GRADE_2  
5. SEMI_GRADE_1  
6. GRADE_1  
7. SPECIAL_GRADE  

## Rank attributes (`RankData.setPlayerAttributesForRank`)

| Rank | maxCE | base regen | damage mult |
|---|---:|---:|---:|
| GRADE_4 | 250 | 2 | 1.0 |
| GRADE_3 | 400 | 2 | 1.0 |
| SEMI_GRADE_2 | 700 | 2 | 1.15 |
| GRADE_2 | 850 | 2 | 1.25 |
| SEMI_GRADE_1 | 1000 | 2 | 1.35 |
| GRADE_1 | 1200 | 2 | 1.45 |
| SPECIAL_GRADE | 1500 | 2 | 1.5 |

Also sets max health / armor / toughness (values in RankData — not all re-listed here; CE and dmg mult verified).

## Ability unlocks by grade

Two parallel trees:

1. **Innate class** — technique abilities via `learnAbilitiesGrade*`  
2. **Learnable** — common combat skills via `Learnable.java` grades  

Full tables: [[04-classes/Grade-unlock-tables]]

## Teaching paths outside grades

| Ability | Status |
|---|---|
| `black_flash` | registry only — **no grade teach** found |
| `blue_mastery` / `red_mastery` / `purple_spark` | registry only — **not** in LimitlessClass |
| Blood sub-skills | not inventory-taught; via holders + BloodControl modes |

Commands: `TeachAbilityCommand`, `/projectjjk teachability…`

## Items related to progression

| Item | Role |
|---|---|
| `cursed_relic_of_affinity` | opens class selection |
| `jujutsu_promotion_letter` | promotion / rank progression |
| `cursed_key` | dungeon unlock flavor |

## Quests / PvE

Present in code (`quest_system`, `pve/dungeon`, ChallengeArena) — secondary to ability codex; not fully expanded in this vault pass.

---

tags: #projectjjk #progression
