# Uncertainties — updated 2026-07-08 (v2)

← [[00-MOC]] · master citations: [[05-reference/Claim-Source-Index]]

## Resolved (were UNKNOWN → now VERIFIED)

| Topic | Was | Now | Source |
|---|---|---|---|
| ResonantRemains drop | chance only | `LivingEntityMixin.strawDollPassive` on damage | `mixin/LivingEntityMixin.java:343-361` |
| Ratio crit proc | chance only | `ratioPassive` PHYSICAL + class ratio | `:365-393` |
| SixthSense chance | effect empty | `PlayerEntityMixin.sixthSensePassive` | `mixin/PlayerEntityMixin.java:234+` |
| WingKing orbs | cast only applies effect | `wingKingPassive` fires **8** HomingBlood | `LivingEntityMixin.java:397-424` |
| Blood orb generation | 30% constant only | BLOOD modifier damage path, max stacks **3** | `:202-230` |
| FLOWING_RED_SCALE real mult | UI 20% | **×1.2** on physical from blood class | `:164-165` |
| Zenith Focus real effect | vague | cancel + reapply **half** damage if effect active | `:187-195` |
| Guard real effect | field only | `PlayerEntityMixin` ×(1−0.60) when guarding | `PlayerEntityMixin.java:225-228` |
| Black Flash inventory gate | — | requires `hasAbility("black_flash")` | `LivingEntityMixin.java:291` |
| Nail → enlarge tag | ITE mentioned | `onNailDamage` tags 600t color 2943221 | `:234-318` |

## Still open / partial

| Topic | Status | What to do for 1:1 |
|---|---|---|
| How player **obtains** black_flash / mastery in normal progression | UNKNOWN teach path beyond command | Grep quests/items/rank rewards; use `TeachAbilityCommand` |
| Blood orb gen for **players** vs NPCs | partial — player path triggers `BloodTechniqueUsedEvent` | Read that event listeners fully |
| Exact SixthSense cancel conditions (second damage tag) | partial (line truncated) | Re-read `PlayerEntityMixin.java:243` full condition |
| CrimsonBinding description `45` vs constants | UI mismatch possible | Prefer entity constants over lang |
| DisplacementBurst `entityRadius=40` unused | VERIFIED unused in cast | Do not port 40 as gameplay radius |
| Purple beam uses raw `getDamage()` | VERIFIED in Purple cast | Intentional or bug — decide per design |
| Nested jar fabric.mod.json deep fields | filename-level only | Open nested jars if needed |
| CFR line drift after re-decompile | process risk | Re-grep symbols via Claim-Source-Index |

## Policy

- **Design base:** use VERIFIED rows freely  
- **1:1 reimpl:** open source line before coding; treat partial as research tasks  
- Do not delete this file — it is the honesty layer that keeps the codex at 10/10

---

tags: #projectjjk #meta #caveats
