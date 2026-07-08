# Version & Identity

← [[00-MOC]]

## Mod identity

| Field | Value | Source | Status |
|---|---|---|---|
| mod id | `jujutsumod` | `.worktrees/nobara-cinematic-slice/src/main/java/jujutsu/mod/JujutsuMod.java:18` · `fabric.mod.json:3` | VERIFIED |
| name | Jujutsu Minecraft | `fabric.mod.json:5` | VERIFIED |
| version | `1.0.0` | `gradle.properties:15` · fabric `${version}` | VERIFIED |
| license | CC0-1.0 | `fabric.mod.json:10` | VERIFIED |
| maven_group | `jujutsu.mod` | `gradle.properties:16` | VERIFIED |
| environment | `*` | `fabric.mod.json:12` | VERIFIED |

## Runtime targets

| Dep | Value | Source | Status |
|---|---|---|---|
| Minecraft | `1.21.8` | `gradle.properties:10` · `fabric.mod.json:29` | VERIFIED |
| Java | `>=21` | `fabric.mod.json:30` | VERIFIED |
| Fabric Loader | `0.19.3` / `>=0.19.3` | `gradle.properties:11` · `fabric.mod.json:28` | VERIFIED |
| Fabric API | `0.136.1+1.21.8` | `gradle.properties:19` | VERIFIED |
| Loom | `1.17-SNAPSHOT` | `gradle.properties:12` | VERIFIED |
| Mappings | official Mojang | `build.gradle:31` | VERIFIED |

## Entrypoints

| Role | Class | Source | Status |
|---|---|---|---|
| main | `jujutsu.mod.JujutsuMod` | `fabric.mod.json:14-16` | VERIFIED |
| client | `jujutsu.mod.client.JujutsuModClient` | `fabric.mod.json:17-19` | VERIFIED |

## Package roots

| Side | Root |
|---|---|
| common | `src/main/java/jujutsu/mod/` |
| client | `src/client/java/jujutsu/mod/client/` |
| test | `src/test/java/jujutsu/mod/` |

## Split environment

**Source:** `.worktrees/nobara-cinematic-slice/build.gradle:17-25` — `loom.splitEnvironmentSourceSets()`, mod binds main+client.  
**Status:** VERIFIED

## Source of truth

| Slicer | Java count | Role |
|---|---:|---|
| checkout (may lag) | ~28 | often Hairpin-VFX-only |
| `.worktrees/nobara-cinematic-slice` | **77** | full Nobara+UI+VFX |

**Status:** VERIFIED (inventory 2026-07-08)

---
tags: #jujutsumod #identity
