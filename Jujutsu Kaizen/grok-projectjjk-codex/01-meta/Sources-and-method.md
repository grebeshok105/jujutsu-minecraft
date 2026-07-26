# Sources & Method

← [[00-MOC]]

## Primary sources

1. **Jar** — `C:\Users\KOMP1\Downloads\projectjjk-1.2.0-1.21.1-fabric-beta.jar`
2. **Zip extract** — `C:\Users\KOMP1\Downloads\projectjjk_extract`
3. **CFR 0.152 decompile** — `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled`
4. **Lang** — `assets/projectjjk/lang/en_us.json`
5. **Datapack** — `data/projectjjk/damage_type/*.json`
6. **Mixins** — `projectjjk.mixins.json`

## Method

```
jar
 ├─ extract (resources, class files, nested jars)
 ├─ CFR decompile of:
 │    ability / class_selection / cursed_energy / cooldown
 │    effect / movesets / network / data / event / util / entity
 └─ 3 parallel verification agents:
      A) AbilityRegistry numbers + grades + passives
      B) Architecture systems + libs + packets
      C) Per-ability cast flows + constants
```

## What is "verified"

| Claim type | How verified |
|---|---|
| dmg / cd / cost | `AbilityRegistry` static block literals |
| extras (range, chance…) | ability class constructor field assigns |
| hold thresholds | `new IntervalThreshold(N)` in ms |
| grade unlocks | `InnateClass` / `Learnable` `teachAbility` calls |
| passive flag | `setPassive(true)` in ctor |
| CE defaults | `PlayerJoinEventHandler` + `RankData` |
| damage types | JSON files under `data/projectjjk/damage_type` |
| library usage | imports in decompiled sources |

## Citation layer (v2)

- Standard: [[Citation-standard]]
- Master map: [[05-reference/Claim-Source-Index]]
- Remaining open items only: [[Uncertainties]]

Major former unknowns **resolved via mixins** (LivingEntityMixin / PlayerEntityMixin):  
ResonantRemains drop, Ratio crit, SixthSense, WingKing orbs, blood orb stacks, Guard/Zenith formulas.

## CFR caveats

- Decompile ≠ original source
- Intermediary names: `class_3222` = ServerPlayer, etc.
- Local variable names reconstructed
- Never copy decompiled Java into jujutsumod (ARR)

## Tooling used

- Java 21 (`D:\WorkFlow\Minecraft\jdk-21.0.11+10`)
- CFR 0.152
- Python inventory scripts
- Grok subagents (read-only)

---

tags: #projectjjk #meta #method
