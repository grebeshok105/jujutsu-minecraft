# Porting Notes → jujutsumod

← [[00-MOC]] · legal: [[01-meta/License-and-legal]]

## Context

| | ProjectJJK | jujutsumod |
|---|---|---|
| MC | 1.21.1 | **1.21.8** |
| Style | full RPG + grades + CE | polish-first kits |
| Nobara | ability hotbar + CE | items + runtime (your branch) |
| License | ARR | your project |

## What to steal as **ideas** (not code)

1. **Hold thresholds** for nail charge (0 / 300 / 800 ms tiers)
2. **Semantic kit loop**: nail → detonate/enlarge/bind → resonance
3. **SUPPRESSED** as anti-technique after resonance
4. **Dependency CDs** between sibling abilities (speech mutual lock)
5. **Grade teach ladder** as progression template
6. **Damage type taxonomy** PHYSICAL vs ABILITY (Black Flash gate)
7. **Rank → max CE** curve if you reintroduce energy

## What you already partially have (cinematic branch)

- ProjectJjk nail entity + marks
- Detonate / enlarge / resonance runtimes
- Character select + hammer/nail items
- Hairpin VFX pipeline (your own)

Numbers **differ** — do not assume registry equals your Profile.

## What NOT to port blindly

| Thing | Why |
|---|---|
| Decompiled Java | ARR + intermediary mess |
| 1.21.1 GeckoLib jar | version mismatch |
| Full 40+ packet surface | overkill |
| Mixin soup | against AGENTS.md policy |
| Blood holder architecture | only if you do Choso |
| Nested Specter | unnecessary |

## Recommended port order (if expanding)

1. Freeze **Nobara feel** numbers you like after playtest
2. Counterplay for marks (visible + cleanse)
3. HUD prepared nails / marks
4. Second character stress-test of architecture
5. Optional CE only if it improves fantasy

## Mapping: ProjectJJK ability → your runtime (approx)

| ProjectJJK | jujutsumod analogue |
|---|---|
| piercing_nail hold | ProjectJjkNailItem use ticks → 1/3/8 |
| hairpin_explosion | detonateMarks |
| hairpin_enlargement | tryEnlargeMarkedTarget |
| resonance | performResonance |
| resonant_remains | (optional, not required) |
| nail_bind_curse | not 1:1; you have embedding marks |

## Verification policy

When claiming "like ProjectJJK":

1. Point to [[05-reference/Claim-Source-Index]] row + ability id
2. Open **file:line** in decompile before coding
3. Run [[05-reference/One-to-one-checklist]]
4. State if rebalanced vs parity

---

tags: #projectjjk #jujutsumod #porting
