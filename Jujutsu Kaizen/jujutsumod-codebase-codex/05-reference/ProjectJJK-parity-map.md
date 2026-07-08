# ProjectJJK ↔ jujutsumod Parity Map

← [[00-MOC]] · ProjectJJK claims: `../grok-projectjjk-codex/05-reference/Claim-Source-Index`  
Our claims: [[Claim-Source-Index]]

Statuses: **SAME** | **INSPIRED** | **DIFFERENT** | **MISSING** | **UNKNOWN**

| ProjectJJK concept | PJJK source (vault) | Our implementation | Our source | Parity |
|---|---|---|---|---|
| Piercing nail 1/3/barrage | PiercingNail holds 0/300/800ms; barrage 10 | hold ticks 6/16 → 1/3/**8** nails | `ProjectJjkNobaraProfile:4-8,63-71` | DIFFERENT counts/units |
| Nail entity maxAge 1200 | NailEntity | `MAX_NAIL_AGE_TICKS=1200` | Profile:9 | SAME |
| Hairpin explosion | ability + nail scan | `detonateMarks` + pending explosions | `RitualRuntime:170` | INSPIRED |
| Hairpin enlarge delay ~20 | ability range 20 / delay in moveset | delay 20 ticks, range 10 | Profile:41-42 | INSPIRED/DIFFERENT ranges |
| Resonance remote | ability CE 100 | shift-hammer, no CE | `performResonance:92` | DIFFERENT delivery |
| Resonant remains body parts | LivingEntityMixin drop 5% | — | — | MISSING |
| Cursed energy | full system | none in kit | no CE in Profile | MISSING |
| Ability hotbar | AbilityRegistry | items + character select | Items + CharacterSelection | DIFFERENT |
| Marks max / duration | ability systems | 4 / 900 ticks | Profile:24-25 | INSPIRED |
| Nail bind curse return | NailBindCurse | not separate ability; embed+marks | entity embed | MISSING as ability |
| ITE blue outline | ITEVisualizer + mixins | TargetMarkPayload + client render | mark payload | INSPIRED |
| Black Flash | passive PHYSICAL | not in kit | — | MISSING |
| Straw doll class grades | grade teach ladder | select Nobara + loadout | CharacterSelection | DIFFERENT progression |

## How to use

1. Open ProjectJJK Claim-Source-Index row  
2. Open our row  
3. Decide SAME vs rebalance vs skip  
4. Follow [[One-to-one-checklist]]

**Status:** tables VERIFIED against both codebases as of 2026-07-08; feel parity UNKNOWN.

---
tags: #jujutsumod #parity #projectjjk
