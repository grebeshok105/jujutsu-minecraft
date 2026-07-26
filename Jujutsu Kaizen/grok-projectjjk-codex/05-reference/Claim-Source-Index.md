# Claim → Source Index

← [[00-MOC]] · standard: [[01-meta/Citation-standard]]

Decompile root: `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled\`  
All paths below relative to that root unless marked **extract:**.

---

## A. Registry (dmg / cd / cost)

| Claim | Source | Status |
|---|---|---|
| `piercing_nail` = 0.5 / 1s / 5 CE / speed 1.0 | `net/hadences/game/system/ability/AbilityRegistry.java:173` | VERIFIED |
| `resonance` = 20 / 20s / 100 CE / suppress 6 | `:172` | VERIFIED |
| `hairpin_enlargement` = 12 / 15 / 15 / range 20 | `:174` | VERIFIED |
| `hairpin_explosion` = 1 / 12 / 30 / range 10 | `:175` | VERIFIED |
| `nail_bind_curse` = 0.5 / 10 / 40 / range 10 | `:176` | VERIFIED |
| `resonant_remains` = 0 / 10 / 35 / drop 5 | `:177` | VERIFIED |
| `black_flash` = 0/0/0 + 5%/×2/8%/15% | `:143` | VERIFIED |
| `purple` = 80 / 40 / 600 | `:163` | VERIFIED |
| `ratio` = 0/0/0 + crit 10% / 1.75 | `:181` | VERIFIED |
| Full 51 puts | `AbilityRegistry.java:142-192` | VERIFIED |

---

## B. Ability base contract

| Claim | Source | Status |
|---|---|---|
| Fields DAMAGE/COOLDOWN/COST/TYPE | `.../ability/Ability.java` ctor ~L52-65 | VERIFIED |
| `onCast` deps + hold dispatch | `Ability.java:109-120` | VERIFIED |
| `addDependentAbility` | `Ability.java:84` | VERIFIED |
| `getHPDamage` Weakness ×0.65 | `Ability.java` getHPDamage method | VERIFIED |
| Cast gate SUPPRESSED blocks INNATE | `network/handlers/C2S/AbilityUseC2SPacketHandler.java:97` | VERIFIED |
| forceHighestCooldown seconds | `cooldown/CooldownManager.java:33` | VERIFIED |

---

## C. Hold thresholds

| Claim | Source | Status |
|---|---|---|
| nail 0 / 300 / 800 | `.../PiercingNail.java:53-55` | VERIFIED |
| blue_mastery 0/500/1500/2500 | `.../BlueMastery.java` holdFunctions | VERIFIED |
| purple_spark 0/2500 | `.../PurpleSpark.java` | VERIFIED |
| piercing_blood 0/1000/2500 | `.../PiercingBlood.java` | VERIFIED |

---

## D. Passives — **where they actually run** (resolved 2026-07-08)

### Black Flash

| Claim | Source | Status |
|---|---|---|
| setPassive | `.../learnable/BlackFlash.java:87` | VERIFIED |
| isBlackFlash PHYSICAL-only + 5% roll | `BlackFlash.java` `isBlackFlash(...)` | VERIFIED |
| Hook: attacker damage modify | `mixin/LivingEntityMixin.java:169` → `blackFlashPassive` | VERIFIED |
| Requires inventory ability | `LivingEntityMixin.java:291` `hasAbility(..., "black_flash")` | VERIFIED |
| On proc original amount → 0 (BF ability applies separate hit) | `LivingEntityMixin.java:296-298` | VERIFIED |
| Not grade-taught | `ability/Learnable.java` (no black_flash) | VERIFIED |

### Guard

| Claim | Source | Status |
|---|---|---|
| 60% field | `AbilityRegistry.java:153` + `Guard.java` | VERIFIED |
| Apply: `newAmount *= 1 - pct/100` when guarding | `mixin/PlayerEntityMixin.java:225-228` | VERIFIED |

### Zenith Focus

| Claim | Source | Status |
|---|---|---|
| chance 30 stored | `AbilityRegistry.java:148` / ZenithFocus ctor | VERIFIED |
| Needs effect ZENITH_FOCUS on victim | `LivingEntityMixin.java:187-195` | VERIFIED |
| Proc: cancel + re-damage `amount/2` | `:191-193` | VERIFIED |
| Roll: `random(100) < getReductionChance()` | `:190` | VERIFIED |

### Ratio crit

| Claim | Source | Status |
|---|---|---|
| chance 10 / mult 1.75 | `Ratio.java:27-34`, registry `:181` | VERIFIED |
| Hook | `LivingEntityMixin.java:177` → `ratioPassive` `:365-393` | VERIFIED |
| Requires PHYSICAL + innate class `"ratio"` | `:370-372` | VERIFIED |
| CD 5s on ability id `ratio` after proc | `:373` | VERIFIED |
| cancel + delayed re-hurt × mult @250ms | `:375-391` | VERIFIED |

### Resonant Remains (body part drop)

| Claim | Source | Status |
|---|---|---|
| chance 5 | `ResonantRemains.java:29-33`, registry `:177` | VERIFIED |
| Hook on damage (attacker has ability) | `LivingEntityMixin.java:179` → `strawDollPassive` `:343-361` | VERIFIED |
| Roll + CD gate on `resonant_remains` | `:353-354` | VERIFIED |
| Spawns `BodyPartEntity` | `:355-359` | VERIFIED |
| Skip if victim is BodyPartEntity | `:350-351` | VERIFIED |

### Sixth Sense

| Claim | Source | Status |
|---|---|---|
| chance 25 / 1 min buff | `AbilityRegistry.java:169`, `SixthSense.java:39,61` | VERIFIED |
| Hook on player damage | `PlayerEntityMixin.java:234` `sixthSensePassive` | VERIFIED |
| Roll + PHYSICAL (or player-attack tag) cancel | `:241-244` | VERIFIED |

---

## E. Straw Doll combat hooks

| Claim | Source | Status |
|---|---|---|
| Nail hit tags target glow | `LivingEntityMixin.java:234-318` `onNailDamage` | VERIFIED |
| Tag duration 600 ticks, color 2943221, dist 20 | `:310-318` | VERIFIED |
| ITEVisualizer map | `util/ITEVisualizer.java:38-47` | VERIFIED |
| Hairpin enlarge requires ITE tag | `HairpinEnlargement.java` ITEVisualizer check | VERIFIED |
| Resonance targets BodyPartEntity | `Resonance.java:87-88` | VERIFIED |
| SUPPRESSED applied duration = 6*20 | `Resonance.java:66,134` | VERIFIED |
| Unlock G4 remains+nail | `class_selection/types/StrawDollClass.java:26-27` | VERIFIED |

---

## F. Blood orbs / Wing King (resolved)

| Claim | Source | Status |
|---|---|---|
| generationChance constant 30 | `Supernova.java:48` | VERIFIED |
| Orb gen on BLOOD-modifier damage (non-player path) 30% | `LivingEntityMixin.java:202-230` | VERIFIED |
| Max stacks 3 | `:228` `new BloodOrbStack(3, le)` | VERIFIED |
| FLOWING_RED_SCALE physical ×1.2 | `LivingEntityMixin.java:164-165` | VERIFIED |
| Wing King: 8 HomingBlood on hit while effect | `LivingEntityMixin.java:397-424` | VERIFIED |

---

## G. CE / Rank

| Claim | Source | Status |
|---|---|---|
| combat sleep stamp | `cursed_energy/CursedEnergySystem.java` | VERIFIED |
| regen skip if sleep | `event/CursedEnergyRegenerationHandler.java:22` | VERIFIED |
| GRADE_4 maxCE 250 regen 2 mult 1.0 | `data/RankData.java:88` | VERIFIED |
| GRADE_3 400 | `:97` | VERIFIED |
| SEMI_G2 700 mult 1.15 | `:106` | VERIFIED |
| GRADE_2 850 mult 1.25 | `:115` | VERIFIED |

---

## H. Unlock / not taught

| Claim | Source | Status |
|---|---|---|
| Learnable grades full | `ability/Learnable.java:13-44` | VERIFIED |
| black_flash absent from Learnable | same file | VERIFIED |
| mastery not in LimitlessClass | `class_selection/types/LimitlessClass.java` | VERIFIED |
| mastery in registry only | `AbilityRegistry.java:164-166` | VERIFIED |
| TeachAbilityCommand exists | `command/TeachAbilityCommand.java` | VERIFIED |
| Debug red mastery VFX | `command/DebugCommand.java:35-36` | VERIFIED |

---

## I. Libraries

| Claim | Source | Status |
|---|---|---|
| jars list | **extract:** `fabric.mod.json` `jars` array | VERIFIED |
| Satin imports | e.g. ability files using `SatinUtil` | VERIFIED |
| Specter particles in BF visuals | `LivingEntityMixin.java:280` SpecterParticleUtils | VERIFIED |
| Specter not needed for CD/CE/registry math | no Specter in Ability/CooldownManager/CE | VERIFIED |

---

## J. Damage types

| Claim | Source | Status |
|---|---|---|
| 26 JSON types | **extract:** `data/projectjjk/damage_type/*.json` | VERIFIED |
| black_flash + purple bypass tags | **extract:** `data/minecraft/tags/damage_type/bypasses_*.json` | VERIFIED |
| PHYSICAL category for BF/Ratio | `util/damage_type/DamageTypeCategories.java` | VERIFIED |

---

## How to use for 1:1

1. Pick claim row  
2. Open decompile file at line  
3. Port behavior, not text  
4. If CFR line drifts after re-decompile: re-grep symbol name  

---

tags: #projectjjk #reference #citations #verified
