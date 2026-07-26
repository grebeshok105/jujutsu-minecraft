# Class Selection

← [[00-MOC]] · unlocks: [[Grade-unlock-tables]]

## Registry (`InnateClassRegistry`)

### Playable (in CLASS_MAP)

| id | Class file |
|---|---|
| `limitless` | LimitlessClass |
| `cursed_speech` | CursedSpeechClass |
| `boogie_woogie` | BoogieWoogieClass |
| `straw_doll` | StrawDollClass |
| `ratio` | RatioClass |
| `blood_manipulation` | BloodManipulationClass |

### Stub constants (string only, **not** put in map)

| id | Class file exists? | Status |
|---|---|---|
| `ten_shadows` | TenShadowsClass | empty grades |
| `black_bird_manipulation` | BlackBirdManipulationClass | empty grades |
| `construction` | ConstructionClass | empty grades |

### Extra stub class

| id | Notes |
|---|---|
| `inverse` | InverseClass exists; **not** even a string constant in registry |

## InnateClass API

```
learnAbilitiesGrade4
learnAbilitiesGrade3
learnAbilitiesSemiGrade2
learnAbilitiesGrade2
learnAbilitiesSemiGrade1
learnAbilitiesGrade1
learnAbilitiesGradeSpecial
```

Each calls `AbilityInventoryData.teachAbility(player, id)` for granted skills.

## Entry item

`cursed_relic_of_affinity` opens class selection UI (ShowClassSelectionScreenPacket).

## Packets

- SetInnateClassPacket (C2S)
- SynchronizeInnateClassPacket (S2C)
- GetInnateClassesPacket / SendInnateClassesPacket

## One-line fantasy per class

| Class | Fantasy |
|---|---|
| Limitless | space control blue/red/purple + infinity shield |
| Cursed Speech | spoken commands in a cone |
| Boogie Woogie | clap teleport swaps + setup |
| Straw Doll | nails → hairpin → resonance |
| Ratio | weak-point crit + overtime vow |
| Blood Manipulation | mode-switched blood tools + wing king |

---

tags: #projectjjk #classes
