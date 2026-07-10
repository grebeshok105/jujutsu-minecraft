# Claim → Source Index (jujutsumod)

← [[00-MOC]] · standard: [[01-meta/Citation-standard]]

**Default path prefix:** `.worktrees/nobara-cinematic-slice/`  
All sources relative to that worktree unless noted.

---

## Mod identity / versions

| Claim | Source | Status |
|---|---|---|
| mod id `jujutsumod` | `src/main/java/jujutsu/mod/JujutsuMod.java:18` | VERIFIED |
| MC 1.21.8 | `gradle.properties:10` | VERIFIED |
| Loader 0.19.3 | `gradle.properties:11` | VERIFIED |
| Fabric API 0.136.1+1.21.8 | `gradle.properties:19` | VERIFIED |
| Java >=21 | `src/main/resources/fabric.mod.json:30` | VERIFIED |
| mod version 1.0.0 | `gradle.properties:15` | VERIFIED |
| license CC0-1.0 | `fabric.mod.json:10` | VERIFIED |

## Entrypoints

| Claim | Source | Status |
|---|---|---|
| main entry | `fabric.mod.json:14-16` | VERIFIED |
| client entry | `fabric.mod.json:17-19` | VERIFIED |
| init order entities→items→particles→sounds→net→ritual→commands | `JujutsuMod.java:23-29` | VERIFIED |
| client register nail renderer | `JujutsuModClient.java:18` | VERIFIED |

## Registries — items

| Claim | Source | Status |
|---|---|---|
| 4 items, ProjectJJK classes | `JujutsuItems.java:12-15` | VERIFIED |
| nail stacksTo 64 | `:12` | VERIFIED |
| hammer durability 256 | `:13` | VERIFIED |

## Registries — entities / particles / sounds

| Claim | Source | Status |
|---|---|---|
| entity `projectjjk_nail` | `JujutsuEntities.java:13` | VERIFIED |
| 8 particle types | `JujutsuParticles.java:10-17` | VERIFIED |
| hairpin + projectjjk sound events | `JujutsuSounds.java:9-29` | VERIFIED |

## Networking payloads

| Claim | Source | Status |
|---|---|---|
| S2C HairpinFx | `JujutsuNetworking.java:17` | VERIFIED |
| S2C NailFlight | `:18` | VERIFIED |
| S2C PreparedNails | `:19` | VERIFIED |
| S2C Impulse | `:20` | VERIFIED |
| S2C TargetMark | `:21` | VERIFIED |
| C2S SelectCharacter | `SelectCharacterPayload.java:8` | VERIFIED |
| S2C CharacterSelectionSync | `CharacterSelectionSyncPayload.java:9` | VERIFIED |
| broadcast radius filter | `JujutsuNetworking.java:42+` | VERIFIED |
| client receivers register | `JujutsuClientNetworking.java:32` | VERIFIED |

## Character selection

| Claim | Source | Status |
|---|---|---|
| NONE/NOBARA enum | `JujutsuCharacter.java` | VERIFIED |
| select + loadout | `CharacterSelectionManager.java:17-25` | VERIFIED |
| keybind register | `JujutsuKeybinds.java:14` | VERIFIED |
| client mixins skin/camera/renderer | `jujutsumod.client.mixins.json` | VERIFIED |

## Nobara profile numbers

| Claim | Source | Status |
|---|---|---|
| hold 6/16 → nails 1/3/8 | `ProjectJjkNobaraProfile.java:4-8,63-71` | VERIFIED |
| max nail age 1200 | `:9` | VERIFIED |
| launch delay 4 * index | `:10,73-75` | VERIFIED |
| launch speed 3.35 | `:13` | VERIFIED |
| target range 36 | `:14` | VERIFIED |
| nail dmg 2 / hairpin 18 | `:19-20` | VERIFIED |
| marks max 4 / 900 ticks | `:24-25` | VERIFIED |
| detonate 4+5*marks | `:31-32,55-56` | VERIFIED |
| enlarge range 10 delay 20 stun 50 dmg 18 | `:41-44` | VERIFIED |
| resonance 96/32 dmg 8+3*marks weakness 80 | `:47-51,59-60` | VERIFIED |

## Nail entity lifecycle

| Claim | Source | Status |
|---|---|---|
| prepare | `ProjectJjkNailEntity.java:69` | VERIFIED |
| launchAt | `:79` | VERIFIED |
| tick | `:133` | VERIFIED |
| embedIn | `:283` | VERIFIED |
| tickEmbedded | `:301` | VERIFIED |
| synced flying/embedded data | `:25-30` | VERIFIED |

## Hammer / nail items

| Claim | Source | Status |
|---|---|---|
| nail release → prepareNails | `ProjectJjkNailItem` releaseUsing | VERIFIED |
| hammer shift → resonance | `ProjectJjkHammerItem` | VERIFIED |
| hammer else launch/enlarge/detonate | same | VERIFIED |

## Runtime methods

| Claim | Source | Status |
|---|---|---|
| prepareNails | `ProjectJjkNobaraRuntime.java:34` | VERIFIED |
| launchHairpin | `:65` | VERIFIED |
| resolveNailImpact | `:97` | VERIFIED |
| markTarget | `ProjectJjkRitualRuntime.java:69/74` | VERIFIED |
| performResonance | `:92` | VERIFIED |
| tryEnlargeMarkedTarget | `:147` | VERIFIED |
| detonateMarks | `:170` | VERIFIED |
| tickHairpinTasks | `:234` | VERIFIED |
| pending queues | `:40-41` | VERIFIED |

## Marks / resonance link

| Claim | Source | Status |
|---|---|---|
| marks map API | `ProjectJjkNailMarks.java:19-47` | VERIFIED |
| resonance bind/get/valid/clear | `ProjectJjkResonanceLink.java:16-29` | VERIFIED |

## VFX / client

| Claim | Source | Status |
|---|---|---|
| HairpinTimeline API | `HairpinTimeline.java:21+` | VERIFIED |
| impulse impact handler | `JujutsuClientNetworking.java:116` | VERIFIED |
| no-falloff SFX | `:176` | VERIFIED |
| screen overlay register | `HairpinScreenOverlay` register | VERIFIED |
| world renderer register | `HairpinWorldRenderer:52` | VERIFIED |

## Assets

| Claim | Source | Status |
|---|---|---|
| 8 particle json | `assets/jujutsumod/particles/` | VERIFIED |
| 38 lang keys | `lang/en_us.json` | VERIFIED |
| shaders present | `shaders/**` | VERIFIED |

## Tests / build

| Claim | Source | Status |
|---|---|---|
| testHairpinTimeline | `build.gradle:38-45` | VERIFIED |
| testHairpinVisualProfile | `:47-54` | VERIFIED |
| testProjectSanity | `:56-63` | VERIFIED |
| testHairpinDebugLog | `:65-72` | VERIFIED |
| testTargetResolver | `:74-81` | VERIFIED |
| testNobaraCombatStateManager | `:83+` | VERIFIED |
| AGENTS baseline build | repo `AGENTS.md` verification policy | VERIFIED |

## Risks

| Claim | Source | Status |
|---|---|---|
| worktree/checkout divergence | file counts | VERIFIED |
| dual runtime stacks | package layout | VERIFIED |
| post-shader bind active in-game | — | UNKNOWN |
| multiplayer mark UX | — | UNKNOWN |

---
tags: #jujutsumod #reference #citations
