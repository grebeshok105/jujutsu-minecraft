# 1:1 Implementation Checklist

← [[00-MOC]] · use with [[Claim-Source-Index]]

Перед «реализуй как в ProjectJJK» пройди чеклист. Не пропускай.

## 0. Setup

- [ ] Decompile root exists: `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled`
- [ ] Extract root exists: `C:\Users\KOMP1\Downloads\projectjjk_extract`
- [ ] Open Claim-Source-Index for the feature

## 1. Numbers

- [ ] dmg / cd / cost from `AbilityRegistry.java` put line
- [ ] extras (range, chance, hold) from ability ctor fields
- [ ] Do **not** trust lang descriptions alone (CrimsonBinding-style mismatches)

## 2. Cast path

- [ ] Ability class hold thresholds
- [ ] `AbilityUseC2SPacketHandler` gates (CE, CD, SUPPRESSED, inventory)
- [ ] dependency `addDependentAbility` list

## 3. Passiveive / damage hooks

- [ ] Search Claim-Source-Index for mixin lines
- [ ] Confirm inventory/`hasAbility` / innate class checks
- [ ] Confirm cancel vs modify-amount vs delayed re-hurt pattern

## 4. Entities / tags

- [ ] Projectile/entity class (e.g. NailEntity maxAge)
- [ ] Tag systems (ITEVisualizer for nails)
- [ ] Damage type JSON + category PHYSICAL/ABILITY

## 5. Presentation (optional)

- [ ] Satin only if you want same flash/shake
- [ ] GeckoLib only if you want same geo
- [ ] Specter **not** required for logic

## 6. jujutsumod diff

- [ ] Compare to cinematic branch / ProjectJjk* runtime
- [ ] Document intentional rebalance vs parity bugs

## Feature templates

### Nobara nail → enlarge → resonance

1. PiercingNail holds `PiercingNail.java:53-55`
2. Nail damage tag `LivingEntityMixin.java:234-318`
3. Enlarge ITE gate `HairpinEnlargement.java`
4. Remains drop `LivingEntityMixin.java:343-361`
5. Resonance BodyPart `Resonance.java:87+` + SUPPRESSED `:134`

### Black Flash

1. Inventory gate + PHYSICAL `BlackFlash` + `LivingEntityMixin:285-300`
2. Damage type tags bypass armor **extract** tags
3. Teach path still UNKNOWN — use command for testing

### Ratio crit

1. Class must be ratio + PHYSICAL
2. `LivingEntityMixin:365-393` full sequence

---

tags: #projectjjk #checklist #1to1
