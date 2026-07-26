# Citation Standard (10/10 rule)

← [[00-MOC]] · [[Sources-and-method]]

## Goal

Every **actionable claim** (number, unlock, hook, formula) must be re-checkable in ≤30 seconds.

## Citation format

```
**Source:** `path/from/decompile/Root.java:LINE` — short quote or fact
**Jar extract (resources):** `path/in/extract/...`
**Status:** VERIFIED | INFERRED | UNKNOWN
```

Paths are relative to:

| Root | Absolute |
|---|---|
| decompile | `C:\Users\KOMP1\Downloads\projectjjk_abilities_decompiled\` |
| extract | `C:\Users\KOMP1\Downloads\projectjjk_extract\` |
| jar | `C:\Users\KOMP1\Downloads\projectjjk-1.2.0-1.21.1-fabric-beta.jar` |

## Status meanings

| Status | Meaning | OK for 1:1 reimpl? |
|---|---|---|
| **VERIFIED** | Literal in decompile/resource at cited line | Yes, still re-read before coding |
| **INFERRED** | Strong chain of calls, no single literal | Re-verify call chain |
| **UNKNOWN** | Not found / incomplete decompile | Do not implement as fact |

## Required anchors by claim type

| Claim type | Must cite |
|---|---|
| dmg/cd/cost | `AbilityRegistry.java` put line |
| hold ms | ability class `IntervalThreshold` line |
| passive proc | mixin/event method + ability getter |
| unlock grade | `*Class.java` or `Learnable.java` teach line |
| CE number | `CursedEnergyData` / `RankData` / effect tick |
| damage type id | `data/projectjjk/damage_type/*.json` and/or `ModDamageTypes` |
| lib need | import site or fabric.mod.json jars entry |

## Master index

All high-value claims: [[05-reference/Claim-Source-Index]]

## Before implementing 1:1

1. Open cited file:line in decompile
2. Confirm intermediary names still match your mental model
3. Diff against jujutsumod if porting behavior
4. Only then write code

---

tags: #projectjjk #meta #citation
