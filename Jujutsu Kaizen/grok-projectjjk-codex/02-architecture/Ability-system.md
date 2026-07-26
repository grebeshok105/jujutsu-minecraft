# Ability System

← [[00-MOC]] · related: [[Cursed-energy]] · [[Cooldown-and-stun]] · [[Networking]] · [[05-reference/Claim-Source-Index]]

## Core class

`net.hadences.game.system.ability.Ability`  
**Source file:** `net/hadences/game/system/ability/Ability.java`

### Fields

| Field | Type | Meaning |
|---|---|---|
| `ID` | String | e.g. `piercing_nail` |
| `ABILITY_IMAGE` | Identifier | icon texture |
| `DAMAGE` | float | base damage |
| `COOLDOWN` | float | seconds |
| `COST` | float | cursed energy |
| `TYPE` | enum | `LEARNED` / `INNATE` / `NULL` |
| `showInInventory` | bool | default true |
| `isPassive` | bool | default false |
| `holdFunctions` | list | hold-threshold branches |
| `holdFunctionNames` | list | UI labels |
| `dependencyAbilities` | map id→seconds | forced CDs on cast |

### Constructor (base)

```
Ability(id, image, damage, cooldownSeconds, costCE, type)
```

Registry typically:

```
new X(id, damage, cooldown, cost, [extras...], type, icon)
```

### Cast pipeline (server)

Handler: `network/handlers/C2S/AbilityUseC2SPacketHandler.java`  
SUPPRESSED gate: **line 97** (INNATE blocked).

1. UUID must match sender
2. Ability exists in registry
3. Ability is in player inventory NBT (`abilities` / `abilities_key`) **or** id is `"dash"` **or** `!showInInventory`
4. CE: `cost > current` → fail
5. Cooldown check → fail if active
6. If type `INNATE` **and** player has `SUPPRESSED` → blocked
7. `onCast(player, heldDurationMs)`:
   - force dependency CDs
   - pick highest hold threshold ≤ heldDuration
   - run function → `Pair<Boolean success, Long extra>`
8. On success:
   - OP may zero CD
   - `startCooldown(id, cooldown + extra)`
   - spend CE
   - fire `CursedEnergyUsedEvent`
   - `CursedEnergySystem.onUseCurseEnergy` (combat sleep stamp)

### Hold system

- Threshold unit: **milliseconds** of hold
- At cast: functions sorted high→low; first match wins
- Multi-hold abilities must have matching `holdFunctionNames` count or throw at load

Examples: see [[05-reference/Hold-thresholds]]

### Damage formula (`getHPDamage`)

```
mult = DamageMultiplierData (or 1.0)
dmg = DAMAGE * mult
if Weakness potion: dmg *= 0.65
return dmg
```

Rank damage mult comes from [[Rank-and-progression]].

### Lifecycle hooks

| Hook | Default |
|---|---|
| `onLoad()` | build description UI |
| `onHold(player)` | empty |
| `onRelease(player)` | broadcast `TriggerAbilityOnReleasePacket` |
| `onCast(player, heldMs)` | hold dispatch |
| `playSound(...)` | abstract |

### Registry

`AbilityRegistry.ABILITY_MAP` — **51** entries.  
Canonical numbers: [[05-reference/Full-registry-table]]

### Inventory / hotbar

- `AbilityInventoryData.teachAbility(player, id)`
- Server/client ability slots
- Hud pointer packets
- Popup menu overlays (blood control etc.)

### Passives

`setPassive(true)` — still in registry; cast may no-op or return false.  
List: [[03-abilities/Passives]]

### Important special cases

| Case | Behavior |
|---|---|
| `dash` | usable even if not in normal inventory path |
| Blood holders | `blood_manipulation` / `blood_utility` delegate to child abilities |
| Showmaker | literally calls learnable `heavy_blow.onCast` mid-combo |

---

tags: #projectjjk #architecture #ability
