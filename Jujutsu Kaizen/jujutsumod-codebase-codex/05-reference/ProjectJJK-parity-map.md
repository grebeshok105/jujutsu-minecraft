# ProjectJJK ↔ jujutsumod Parity Map

← [[00-MOC]] · ProjectJJK claims: `../grok-projectjjk-codex/05-reference/Claim-Source-Index`  
Our claims: [[Claim-Source-Index]] · [[../03-systems/Nobara-runtime-flow]]

Statuses: **SAME** | **INSPIRED** | **DIFFERENT** | **MISSING** | **UNKNOWN**

| ProjectJJK concept | PJJK source (vault) | Our implementation | Our source | Parity |
|---|---|---|---|---|
| Piercing nail 1/3/barrage | PiercingNail holds 0/300/800ms; barrage 10 | hold ticks 6/16 → 1/3/**8** nails | `ProjectJjkNobaraProfile.java:4-8,63-71` | DIFFERENT counts/units |
| Nail entity maxAge 1200 | NailEntity | `MAX_NAIL_AGE_TICKS=1200` | `ProjectJjkNobaraProfile.java:9` | SAME |
| Hairpin explosion | `hairpin_explosion` dmg 1 / range 10; nail scan + staggered Flash32VFX | B action → `detonateMarks`; fixed 12 dmg, radius 1.5, staggered server resolution, `explosion` VFX recipe | `ProjectJjkNobaraProfile.java:30-37`, `ProjectJjkRitualRuntime.java:185-207`, `NobaraVfxRecipes.java:142-162` | INSPIRED/DIFFERENT damage and range |
| Hairpin enlarge | `hairpin_enlargement` dmg 12 / range 20; snap + delayed target hit + flash-strike/spark VFX | R action → `tryEnlargeMarkedTarget`; delay 20 ticks, range 20, damage 16, `enlarge` VFX recipe | `ProjectJjkNobaraProfile.java:40-44`, `ProjectJjkRitualRuntime.java:160-182,312-337`, `NobaraVfxRecipes.java:118-140` | INSPIRED; range/delay same, damage different |
| Resonance remote | ability CE 100 | shift-hammer, no CE | `ProjectJjkRitualRuntime.java:92+`, `ProjectJjkHammerItem.java:20-24` | DIFFERENT delivery |
| Resonant remains body parts | LivingEntityMixin drop 5% | — | — | MISSING |
| Cursed energy | full system | none in kit | no CE in Profile | MISSING |
| Ability hotbar | AbilityRegistry | V character select + explicit Enlarge/Boom keybinds; no ProjectJJK hotbar | `CharacterSelectScreen.java:108-147`, `JujutsuKeybinds.java:25-36` | DIFFERENT |
| Marks max / duration | ability systems | 4 / 900 ticks | `ProjectJjkNobaraProfile.java:23-27` | INSPIRED |
| Nail bind curse return | NailBindCurse | not separate ability; embed+marks | entity embed | MISSING as ability |
| ITE blue outline | ITEVisualizer + mixins | server-applied vanilla Glowing with cyan scoreboard team; no mark payload/renderer manager | `ProjectJjkRitualRuntime.java:87-96,510-581`, `ProjectSanityTest.java:287-301` | INSPIRED |
| Black Flash | passive PHYSICAL | not in kit | — | MISSING |
| Straw doll class grades | grade teach ladder | select Nobara + loadout | CharacterSelection | DIFFERENT progression |

## How to use

1. Open ProjectJJK Claim-Source-Index row  
2. Open our row  
3. Decide SAME vs rebalance vs skip  
4. Follow [[One-to-one-checklist]]

**Status:** updated after explicit Hairpin Enlarge/Boom patch on 2026-07-08. Feel parity still needs in-game smoke test.

---
tags: #jujutsumod #parity #projectjjk
