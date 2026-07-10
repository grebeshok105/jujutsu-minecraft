# Uncertainties

← [[00-MOC]] · [[Citation-standard]]

## Structural

| Topic | Status | Notes |
|---|---|---|
| Checkout lag vs full kit | VERIFIED | 28 vs 77 Java files; cite cinematic worktree |
| Which branch is “release” | UNKNOWN product | Document cinematic as feature SoT |

## Runtime (not smoke-tested this pass)

| Topic | Status | Verify via |
|---|---|---|
| Full combat loop feel | UNKNOWN | `runClient` + nails/hammer/shift |
| Multiplayer mark sync | UNKNOWN | 2 clients, mark payload radius |
| Character select perf | INFERRED risk | open screen, watch FPS |
| Post-shader bind active | UNKNOWN | resource reload + render path |
| No-falloff SFX comfort | UNKNOWN | ear test |

## Dual / legacy code

| Topic | Status | Source |
|---|---|---|
| ProjectJJK items are default for all 4 ids | VERIFIED | `JujutsuItems.java:12-15,26-34` |
| Legacy Hairpin runtime still in tree | VERIFIED path exists | `character/nobara/NobaraHairpinRuntime.java` |
| No cursed energy in profile | VERIFIED | no CE constants in `ProjectJjkNobaraProfile` |

---
tags: #jujutsumod #uncertainties
