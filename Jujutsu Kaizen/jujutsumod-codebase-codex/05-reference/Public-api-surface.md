# Public API Surface (for future changes)

← [[00-MOC]]

“Точки входа”, которые безопасно расширять. Не “Java public” только — **product extension points**.

## Prefer calling / extending

| Surface | Why | Source |
|---|---|---|
| `JujutsuMod.id` | resource locations | `JujutsuMod.java:33` |
| `JujutsuCharacter` enum | new characters | enum file |
| `CharacterSelectionManager.select` | selection side effects | `:17` |
| `ProjectJjkNobaraProfile` constants | balance tuning | profile file |
| `ProjectJjkNobaraRuntime` / `RitualRuntime` | combat flow | runtime files |
| `JujutsuNetworking.broadcast*` | S2C patterns | networking |
| `TargetResolver` | aiming | combat |
| `HairpinTimeline` / `HairpinVisualProfile` | VFX timing budgets | fx |
| Client: `JujutsuClientNetworking` kind handlers | new impulse kinds | client net |

**Status:** VERIFIED as existing hubs.

## Avoid direct coupling

| Anti-pattern | Why |
|---|---|
| Client writing marks map | server-authoritative |
| Spawning nails from client | desync |
| New Fabric impl imports | AGENTS forbid |
| Broad new mixins | only if API missing |
| Copying ProjectJJK decompile | ARR |

## Loadout extension

`ProjectJjkNobaraLoadout.ensureStarterTools` — called on select Nobara.  
**Source:** `CharacterSelectionManager.java:23-25`  
**Status:** VERIFIED

## Commands

`JujutsuCommands` — debug/give/hairpin probes. Not player progression API.

---
tags: #jujutsumod #api
