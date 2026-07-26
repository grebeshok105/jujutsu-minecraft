# Cursed Energy (CE)

← [[00-MOC]] · [[Ability-system]] · [[Rank-and-progression]]

## Components

| Class | Role |
|---|---|
| `CursedEnergyData` | NBT current/max/regen rates + S2C sync |
| `CursedEnergySystem` | combat-sleep timestamps |
| `CursedEnergyRegenerationHandler` | end-server-tick regen |
| `CursedEnergyUsedEvent` | refunds (OP, Six Eyes) |

## NBT keys (`CursedEnergyData`)

| Key | Meaning |
|---|---|
| `cursed_energy` | current |
| `max_cursed_energy` | cap |
| `ce_regen_timeout` | combat sleep seconds (blocks regen) |
| `ce_regen_rate` | base regen per tick while not sleeping |
| `ce_rest_regen_rate` | regen while player sleeping |

Clamp: energy ∈ [0, max], all rates ≥ 0.

## Combat sleep

```
onUseCurseEnergy(player) → stamp now millis
isCurseEnergySleep(player, timeoutSec) → true if now - stamp < timeout*1000
```

While sleeping (in combat window): **no regen**.

## Regen (each server tick)

```
if combat sleep → skip
if energy >= max → skip
else energy += sleeping ? restRate : baseRate
```

## Join defaults (if zero)

| Stat | Default |
|---|---|
| regen timeout | **4** s |
| base regen | **2** / tick |
| rest regen | **50** / tick |
| max energy | **0** until rank/set |

## Rank → max CE (from `RankData`)

| Rank | maxCE | base regen | dmg mult |
|---|---:|---:|---:|
| GRADE_4 | 250 | 2 | 1.0 |
| GRADE_3 | 400 | 2 | 1.0 |
| SEMI_GRADE_2 | 700 | 2 | 1.15 |
| GRADE_2 | 850 | 2 | 1.25 |
| SEMI_GRADE_1 | 1000 | 2 | 1.35 |
| GRADE_1 | 1200 | 2 | 1.45 |
| SPECIAL_GRADE | 1500 | 2 | 1.5 |

## Continuous drains (`EffectManagerTick`)

| Effect | Cost | Notes |
|---|---|---|
| Reverse Cursed Technique | **30 CE / tick** | also Regen V + Resistance I for 1 tick; strip if CE < 30 |
| Infinity | **10 CE / second** (every 20 ticks) | strip if CE < 10 or class ≠ limitless |

## Refunds on spend event

| Condition | Refund |
|---|---|
| OP mode | 100% of spent |
| SIX_EYES effect | **50%** of spent |

## Commands

| Command | Perm | Notes |
|---|---|---|
| `/setmaxcursedenergy <value>` | 2 | SetMaxCursedEnergyCommand |
| `/projectjjk setmaxce <value>` | 4 | ProjectJJKCommand |

## Sync packets

- SynchronizeCursedEnergyPacket
- SynchronizeMaxCursedEnergyPacket
- SynchronizeCERegenerationTimeoutPacket
- SynchronizeCEBaseRegenerationRatePacket
- SynchronizeCERestRegenerationRatePacket

## Design takeaway for jujutsumod

CE is a full resource economy: max by rank, combat-gated regen, continuous drain for toggles, refunds for Six Eyes.  
Your project removed energy from Nobara kit once — if reintroducing, decide **toggle drains** vs **per-cast only**.

---

tags: #projectjjk #architecture #ce
