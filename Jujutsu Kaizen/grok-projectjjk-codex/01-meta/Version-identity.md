# Version & Identity

← [[00-MOC]]

## Jar

| Field | Value |
|---|---|
| File | `projectjjk-1.2.0-1.21.1-fabric-beta.jar` |
| Path checked | `C:\Users\KOMP1\Downloads\projectjjk-1.2.0-1.21.1-fabric-beta.jar` |
| mod id | `projectjjk` |
| version | `1.2.0-1.21.1-fabric-beta` |
| name | ProjectJJK |
| author | hadences |
| homepage | https://github.com/hadences |
| license | **All Rights Reserved** |
| environment | `*` |

## Runtime targets

| Dep | Constraint |
|---|---|
| Minecraft | `~1.21.1` |
| Java | `>=21` |
| Fabric Loader | `>=0.16.7` |
| Fabric API | `*` |

## Entrypoints

| Role | Class |
|---|---|
| main | `net.hadences.ProjectJJK` |
| client | `net.hadences.ProjectJJKClient` |
| fabric-datagen | `net.hadences.ProjectJJKDataGenerator` |

## Other metadata

- `accessWidener`: `projectjjk.accesswidener`
- mixins: `projectjjk.mixins.json` (see [[02-architecture/Mixins]])
- jar-in-jar list: see [[02-architecture/Libraries]]

## Class count (extract)

| Metric | Count |
|---|---|
| `.class` total | ~861 |
| ability package classes | ~123 |
| decompiled ability-related `.java` (expanded) | ~405 |

## Not the same as jujutsumod

| | ProjectJJK | jujutsumod |
|---|---|---|
| MC | 1.21.1 | 1.21.8 |
| mod id | projectjjk | jujutsumod |
| approach | full JJK RPG | polish-first kits |
| license | ARR | project policy |

---

tags: #projectjjk #meta
