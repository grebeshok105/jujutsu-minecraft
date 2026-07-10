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
| Java >=21 | `src/main/resources/fabric.mod.json:30` | VERIFIED |
| mod version 1.0.0 | `gradle.properties:15` | VERIFIED |

## Entrypoints

| Claim | Source | Status |
|---|---|---|
| main entry | `fabric.mod.json:14-16` | VERIFIED |
| client entry | `fabric.mod.json:17-19` | VERIFIED |
| init order entities→items→particles→sounds→networking→ProjectJJK ritual→commands | `JujutsuMod.java:23-29` | VERIFIED |
| client registers real ProjectJJK nail renderer and current VFX/networking/input | `JujutsuModClient.java:14-22` | VERIFIED |
| removed legacy `HairpinPlaybackManager` / `NobaraNailFlightManager` are not initialized | `JujutsuModClient.java:14-22`, guard `ProjectSanityTest.java:163-220` | VERIFIED |

## Registries — items

| Claim | Source | Status |
|---|---|---|
| default `hairpin_nail` uses `ProjectJjkNailItem` | `JujutsuItems.java:12` | VERIFIED |
| default `straw_doll_hammer` uses `ProjectJjkHammerItem` | `JujutsuItems.java:13` | VERIFIED |
| ProjectJJK alias items use the same ProjectJJK classes | `JujutsuItems.java:14-15` | VERIFIED |
| old default item models were removed; default item definitions point to ProjectJJK models | `ProjectSanityTest.java:171-174`, `ProjectSanityTest.java:195-199` | VERIFIED |

## Registries — entities / particles / sounds

| Claim | Source | Status |
|---|---|---|
| entity `projectjjk_nail` | `JujutsuEntities.java:13` | VERIFIED |
| 8 Hairpin particle types remain for ProjectJJK runtime/VFX | `JujutsuParticles.java:10-17` | VERIFIED |
| hairpin + projectjjk sound events remain | `JujutsuSounds.java:9-29` | VERIFIED |

## Networking payloads

| Claim | Source | Status |
|---|---|---|
| S2C typed VFX cue registered | `JujutsuNetworking.java:18` | VERIFIED |
| S2C character selection sync registered | `JujutsuNetworking.java:19` | VERIFIED |
| C2S character select registered | `JujutsuNetworking.java:20` | VERIFIED |
| C2S Nobara action registered | `JujutsuNetworking.java:21` | VERIFIED |
| server handles Nobara action through `ProjectJjkNobaraActions.tryCast` | `JujutsuNetworking.java:29-36` | VERIFIED |
| client receives only typed VFX cues and character selection sync | `JujutsuClientNetworking.java:13-19` | VERIFIED |
| removed legacy S2C VFX payloads are guarded against re-registration | `ProjectSanityTest.java:163-220,349-363` | VERIFIED |
| VFX cue broadcast uses radius filtering and `canSend` | `JujutsuNetworking.java:38-60` | VERIFIED |

## Character selection

| Claim | Source | Status |
|---|---|---|
| NONE/NOBARA enum | `JujutsuCharacter.java` | VERIFIED |
| select + loadout | `CharacterSelectionManager.java:17-25` | VERIFIED |
| keybind register | `JujutsuKeybinds.java:14` | VERIFIED |
| client mixins skin/camera/renderer | `jujutsumod.client.mixins.json` | VERIFIED |

## Nobara runtime

| Claim | Source | Status |
|---|---|---|
| canonical runtime package is `character/nobara/projectjjk` | `src/main/java/jujutsu/mod/character/nobara/projectjjk/` | VERIFIED |
| old runtime classes removed | `ProjectSanityTest.java:159-164` | VERIFIED |
| old legacy payload/playback classes removed | `ProjectSanityTest.java:165-170` | VERIFIED |
| ProjectJJK prepared/flying nail aura is rendered by the real entity renderer | `ProjectJjkNailRenderer.java:85-90`, `:115-183` | VERIFIED |
| transient Nobara scenes use registered VFX recipes; removed `HairpinWorldRenderer` is not a live path | `NobaraVfxRecipes.java:23-34`, guard `ProjectSanityTest.java:349-363` | VERIFIED |

## Nobara profile numbers

| Claim | Source | Status |
|---|---|---|
| hold timing adds one nail per 10 ticks after first nail | `ProjectJjkNobaraProfile.java`, test `ProjectJjkNobaraProfileTest.java:28-37` | VERIFIED |
| max nail age 1200 | `ProjectJjkNobaraProfile.java`, test `ProjectJjkNobaraProfileTest.java:40-43` | VERIFIED |
| launch delay 4 ticks per nail index | `ProjectJjkNobaraProfile.java`, test `ProjectJjkNobaraProfileTest.java:47-50` | VERIFIED |
| launch range around prepared nails is 2 blocks | `ProjectJjkNobaraProfile.java`, test `ProjectJjkNobaraProfileTest.java:54-61` | VERIFIED |
| Enlarge damage 16 / Boom base damage 12 fixed | `ProjectSanityTest.java:229-234`, `ProjectJjkNobaraProfileTest.java:107-111` | VERIFIED |

## Nail entity lifecycle

| Claim | Source | Status |
|---|---|---|
| prepare creates non-launched real nail entity | `ProjectJjkNailEntity.java:69-76` | VERIFIED |
| launchAt stores target/delay/explosive flag | `ProjectJjkNailEntity.java:78-95` | VERIFIED |
| tick handles prepared, delayed launch, hit, embed, explosion pass-through | `ProjectJjkNailEntity.java:132-204` | VERIFIED |
| embed uses body-space offset and body yaw | `ProjectJjkNailEntity.java:280-299` | VERIFIED |
| renderer uses synced body anchor for embedded nails | `ProjectJjkNailRenderer.java:65-74`, `:82-95` | VERIFIED |

## Client VFX

| Claim | Source | Status |
|---|---|---|
| one `VfxCuePayload` receiver delegates to `VfxDirector`; it has no effect-ID switch | `JujutsuClientNetworking.java:13-19` | VERIFIED |
| all ten Nobara IDs register Java recipes | `NobaraVfxRecipes.java:23-34` | VERIFIED |
| director owns world/HUD callbacks, tick, disconnect cleanup, unknown-ID safety, and a 64-instance bound | `VfxDirector.java:24-132` | VERIFIED |
| no-falloff SFX is a director channel | `VfxSoundChannel.java:12-27`, `VfxContext.java:92-94` | VERIFIED |
| removed overlay/world/camera/playback managers are guarded as absent | `ProjectSanityTest.java:349-363` | VERIFIED |
| first-person snap uses `VfxFirstPersonChannel`; the narrow hand mixin only reads director state | `NobaraVfxRecipes.java:164-166`, `NobaraFirstPersonSnapMixin.java:24` | VERIFIED |

## Assets

| Claim | Source | Status |
|---|---|---|
| 8 particle json | `assets/jujutsumod/particles/` | VERIFIED |
| runtime item models are ProjectJJK nail/hammer models only | `ProjectSanityTest.java:171-174`, `:195-199` | VERIFIED |
| old unused Hairpin post-shaders removed | `ProjectSanityTest.java:173-174` | VERIFIED |

## Tests / build

| Claim | Source | Status |
|---|---|---|
| testProjectSanity | `build.gradle:41-48` | VERIFIED |
| testTargetResolver | `build.gradle:50-57` | VERIFIED |
| testProjectJjkNobaraProfile | `build.gradle:59-66` | VERIFIED |
| testVfxCore | `build.gradle:71-78` | VERIFIED |
| testVfxTimeline | `build.gradle:81-88` | VERIFIED |
| testVfxQuality | `build.gradle:91-98` | VERIFIED |
| testVfxAnchor | `build.gradle:101-108` | VERIFIED |
| check depends on all seven current assertion tasks | `build.gradle:111-119` | VERIFIED |
| removed legacy test tasks are absent from `check` | `build.gradle:41-119` | VERIFIED |

## Risks

| Claim | Source | Status |
|---|---|---|
| worktree/checkout divergence can still mislead agents | [[../06-maintenance/Risks-and-tech-debt]] | VERIFIED |
| dual runtime stack risk resolved by deletion | `ProjectSanityTest.java:159-188` | VERIFIED |
| no automated in-game smoke in CI | [[../06-maintenance/Risks-and-tech-debt]] | VERIFIED |

---
tags: #jujutsumod #reference #citations
