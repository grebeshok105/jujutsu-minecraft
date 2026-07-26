# Passives

← [[_index]] · citations: [[05-reference/Claim-Source-Index]]

All five call `setPassive(true)`. **Runtime hooks are mostly mixins**, not `onCast`.

## Inventory

| id | setPassive | Registry | Runtime hook |
|---|---|---|---|
| black_flash | `BlackFlash.java:87` | `AbilityRegistry.java:143` | `LivingEntityMixin.blackFlashPassive` `:285-300` called from `:169` |
| guard | `Guard.java:34` | `:153` | `PlayerEntityMixin` `:225-228` |
| zenith_focus | `ZenithFocus.java:34` | `:148` | `LivingEntityMixin` `:187-195` |
| ratio | `Ratio.java:34` | `:181` | `LivingEntityMixin.ratioPassive` `:365-393` |
| resonant_remains | `ResonantRemains.java:34` | `:177` | `LivingEntityMixin.strawDollPassive` `:343-361` |

---

## Black Flash — VERIFIED

**Source registry:** `AbilityRegistry.java:143`  
`chanceBF=5`, `amp=2`, `ceRestore=8%`, `cdReduce=15%`

**Source logic:**

1. `LivingEntityMixin.java:169` modifies outgoing damage amount via `blackFlashPassive`
2. Requires `AbilityInventoryData.hasAbility(player, "black_flash")` (`:291`)
3. `BlackFlash.isBlackFlash` — PHYSICAL category only, roll ≤5%
4. On success: returns **0.0f** for normal damage (`:296-298`) while ability applies separate BLACK_FLASH hit (in BlackFlash class castBlackFlash)

**Unlock:** not in `Learnable.java` — **no grade teach** VERIFIED.

**Status:** VERIFIED (proc path). Teach path UNKNOWN beyond commands.

---

## Guard — VERIFIED

**Source:** registry `:153` extras `60`; `PlayerEntityMixin.java:225-228`

```
if (isGuarding()) newAmount *= 1.0f - damageReductionPercentage/100.0f  // 60% → ×0.4
```

Cast of ability returns false (not an active cast button).

**Unlock:** `Learnable.java:14` Grade4.

---

## Zenith Focus — VERIFIED (corrected)

Not a vague DR. When victim has `ModEffects.ZENITH_FOCUS`:

**Source:** `LivingEntityMixin.java:187-195`

- roll `random(100) < getReductionChance()` (30)
- on success: **cancel** damage, re-apply `amount / 2.0f`

So: 30% chance to take **half** while effect is active (effect application is separate).

**Unlock:** `Learnable.java:27` SemiGrade2.

---

## Ratio — VERIFIED

**Source registry:** `:181` — crit 10%, mult 1.75  
**Source proc:** `LivingEntityMixin.ratioPassive` `:365-393`

Conditions:

1. damage category **PHYSICAL**
2. attacker is ServerPlayer
3. innate class == `"ratio"`
4. `random(100) < getRatioCritChance()` (10)
5. not on CD for ability id `ratio`

On proc:

- startCooldown(`ratio`, **5** seconds) — note: not 0 from registry
- amount × 1.75
- cancel original, re-hurt after **250ms**
- Satin shake + impact frame RATIO_CRIT

**Unlock:** `RatioClass` Grade4.

---

## Resonant Remains — VERIFIED

**Source chance:** `ResonantRemains.java:29-33` / registry `:177` value **5**  
**Source drop:** `LivingEntityMixin.strawDollPassive` `:343-361`

On **damage** (not only death): if attacker player has ability `resonant_remains`:

- skip if victim is BodyPartEntity
- if `random(100) < 5` and not on CD:
  - startCooldown `resonant_remains` for registry CD (10s)
  - spawn `BodyPartEntity` at victim pos +0.5y

**Unlock:** `StrawDollClass.java:26` Grade4.

---

tags: #projectjjk #passives #verified
