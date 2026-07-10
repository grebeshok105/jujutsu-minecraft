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
| init order entities→data components→items→particles→sounds→networking→ProjectJJK ritual runtimes→commands | `JujutsuMod.java:25-33` | VERIFIED |
| client registers Straw Doll renderer factory, real ProjectJJK nail renderer, and current VFX/networking/input | `JujutsuModClient.java:15-24` | VERIFIED |
| removed legacy `HairpinPlaybackManager` / `NobaraNailFlightManager` are not initialized | `JujutsuModClient.java:14-22`, guard `ProjectSanityTest.java:163-220` | VERIFIED |

## Registries — items

| Claim | Source | Status |
|---|---|---|
| default `hairpin_nail` uses `ProjectJjkNailItem` | `JujutsuItems.java:14` | VERIFIED |
| default `straw_doll_hammer` uses `ProjectJjkHammerItem` | `JujutsuItems.java:15` | VERIFIED |
| ProjectJJK alias items use the same ProjectJJK classes | `JujutsuItems.java:16-17` | VERIFIED |
| `resonance_remnant` is a non-stackable typed target-link item | `JujutsuItems.java:18,42-45`; `ProjectJjkResonanceRemnant.java:14-28` | VERIFIED |
| `straw_doll` is a non-stackable reusable GeckoLib item | `JujutsuItems.java:19,47-50`; `ProjectJjkStrawDollItem.java:19-69` | VERIFIED |
| Nobara starter tools include hammer, doll, and up to 16 nails but no remnant | `ProjectJjkNobaraLoadout.java:8-23` | VERIFIED |
| `resonance_target` persists and network-syncs remnant target UUID/dimension/name | `JujutsuDataComponents.java:10-20`; `ProjectJjkResonanceRemnant.java:14-28` | VERIFIED |
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
| removed legacy S2C VFX payloads are guarded against re-registration | `ProjectSanityTest.java:163-220,372-377` | VERIFIED |
| VFX cue broadcast uses radius filtering and `canSend` | `JujutsuNetworking.java:38-60` | VERIFIED |
| `VfxCue` carries immutable origin fallback, optional anchor ID, and world-space `anchorOffset` | `VfxCue.java:6-15`; test `VfxCueTest.java:19-47` | VERIFIED |
| typed payload writes and reads `anchorOffset` immediately after `anchorEntityId` | `VfxCuePayload.java:16-37`; test `VfxCueTest.java:38-47` | VERIFIED |

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
| transient Nobara scenes use registered VFX recipes; removed `HairpinWorldRenderer` is not a live path | `NobaraVfxRecipes.java:23-34,37-189`, guard `ProjectSanityTest.java:360-377` | VERIFIED |

## Straw Doll Resonance

| Claim | Source | Status |
|---|---|---|
| every second accepted ordinary nail damage hit for one caster/target pair drops a target-bound remnant at the wound; rejected/explosive/self hits do not advance | `ProjectJjkNobaraRuntime.java:148-153`; `ProjectJjkRitualPolicy.java:45-47`; `ProjectJjkStrawDollRuntime.java:36-40,60-86`; `ProjectJjkRemnantProgress.java:7-24` | VERIFIED implementation; Minecraft adaptation |
| Resonance start requires main-hand hammer, offhand doll, matching remnant, nail, alive loaded target, same dimension, finite distance <=64, and no duplicate cast | `ProjectJjkStrawDollRuntime.java:88-112,139-203`; `ProjectJjkRitualPolicy.java:3-54` | VERIFIED implementation; range/timing are adaptations |
| the 14-tick ritual revalidates requirements every server tick and does not require line of sight | `ProjectJjkStrawDollRuntime.java:100-136,167-203` | VERIFIED |
| nail and exact remnant are both located before either shrinks; only successful impact consumes them | `ProjectJjkStrawDollRuntime.java:211-215,246-275` | VERIFIED |
| successful Resonance damages/weakens target, consumes marks, discards owned embedded nails, clears glow, and emits caster/target cues | `ProjectJjkStrawDollRuntime.java:217-243` | VERIFIED |
| disconnect/caster death/target death-or-unload/server stop clear relevant pending/progress state | `ProjectJjkStrawDollRuntime.java:45-57,315-323` | VERIFIED |
| all new Straw Doll transient particles/sounds use VFX Core cues; common ritual runtime has no direct particle/sound composition | `ProjectJjkStrawDollRuntime.java`; guard `ProjectSanityTest.java:481-484` | VERIFIED |
| canonical invariant is meaningful link + effigy/proxy + hammer-driven nail; Hairpin is separate | `docs/research/2026-07-10-nobara-straw-doll-canon.md:24-50,127-152` | VERIFIED research |
| hit threshold, wind-up, resource consumption, and 64-block/same-dimension/loaded-target policy are Minecraft rules, not universal canon claims | `docs/research/2026-07-10-nobara-straw-doll-canon.md:45,50-51,139-152,208-216`; [[../03-systems/Straw-Doll-resonance]] | VERIFIED classification |

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
| all 14 Nobara IDs register Java recipes | `NobaraVfxRecipes.java:25-38` | VERIFIED |
| director owns world/HUD callbacks, tick, unknown-ID safety, a 64-instance bound, `ClientLevel` identity cleanup, and null/disconnect reset | `VfxDirector.java:25-148`, guard `ProjectSanityTest.java:321-337` | VERIFIED |
| non-expired late cues receive actual `initialAgeTicks`; one-shot opening beats run only below two ticks; all 23 timed Nobara channel calls preserve age | `VfxTimeline.java:10-27`, `NobaraVfxRecipes.java:41-234`, guard `ProjectSanityTest.java:388` | VERIFIED |
| HUD, camera/FOV, first-person, and post-process realtime starts are offset to the late cue phase | `VfxTimeline.java:22-27`, `NobaraVfxRecipes.java:41-234`, `VfxFirstPersonChannel.java:14-27`, `VfxPostProcessChannel.java:11-20` | VERIFIED |
| live world anchors resolve as `anchor.position() + anchorOffset`; missing anchors fall back to immutable `cue.origin()` | `VfxAnchorResolver.java:9-15`, `VfxWorldChannel.java:34-69`, test `VfxAnchorResolverTest.java:16-40` | VERIFIED |
| unanchored server cues use `Vec3.ZERO`; anchored Nobara cues store `origin.subtract(anchor.position())` | `ProjectJjkNobaraRuntime.java:233-238`, `ProjectJjkRitualRuntime.java:601-606` | VERIFIED |
| no-falloff SFX is a director channel | `VfxSoundChannel.java:12-27`, `VfxContext.java:92-94` | VERIFIED |
| removed overlay/world/camera/playback managers are guarded as absent | `ProjectSanityTest.java:372-377` | VERIFIED |
| first-person snap uses an age-aware `VfxFirstPersonChannel` start, lasts 0.75 seconds, and traverses the full `0..15` phase; the narrow hand mixin only reads director state | `NobaraVfxRecipes.java:188-189`, `VfxFirstPersonChannel.java:14-59`, `ProjectSanityTest.java:380-393`, `NobaraFirstPersonSnapMixin.java:24` | VERIFIED |
| `REMNANT_DROP`, `RITUAL_BIND`, `DOLL_STRIKE`, and `RESONANCE_RELEASE` have stable IDs, server-authoritative emitters, and registered recipes | `NobaraVfxIds.java:17-20`; `ProjectJjkStrawDollRuntime.java:84-85,106-107,236-239`; `NobaraVfxRecipes.java:35-38` | VERIFIED |
| named camera profiles cover launch, heavy impact, explosion, and ritual with clamped cumulative yaw/pitch/FOV | `VfxCameraChannel.java:12-61` | VERIFIED |
| director-owned post-process calls public vanilla `processBlurEffect()` and disables only blur for the session on runtime/linkage failure | `VfxPostProcessChannel.java:7-46`, guard `ProjectSanityTest.java:445` | VERIFIED wiring; in-game feel UNKNOWN |

## Assets

| Claim | Source | Status |
|---|---|---|
| 8 particle json | `assets/jujutsumod/particles/` | VERIFIED |
| runtime item models include ProjectJJK nail/hammer and original Straw Doll/remnant resources | `ProjectSanityTest.java:195-199,480-534` | VERIFIED |
| original Straw Doll source/runtime set includes 25 matching source/runtime cubes, 14 source animator tracks/four runtime clips, portable texture path, bounded 64x64 Box UVs, deterministic texture source, and texture-backed previews | `source-assets/blockbench/straw_doll.bbmodel:11-202`; `assets/jujutsumod/geckolib/models/straw_doll.geo.json`; `assets/jujutsumod/geckolib/animations/straw_doll.animation.json`; guard `ProjectSanityTest.java:495-563` | VERIFIED resource completeness + native Blockbench MCP inspection; in-game presentation UNKNOWN |
| copied ProjectJJK doll geometry/animation/texture are absent from runtime assets | guard `ProjectSanityTest.java:557-563`; deleted `assets/jujutsumod/{geo,animations,textures}/projectjjk/doll*` | VERIFIED |
| old unused Hairpin post-shaders removed | `ProjectSanityTest.java:173-174` | VERIFIED |

## Tests / build

| Claim | Source | Status |
|---|---|---|
| testProjectSanity | `build.gradle:41-48` | VERIFIED |
| testTargetResolver | `build.gradle:50-57` | VERIFIED |
| testProjectJjkNobaraProfile | `build.gradle:59-66` | VERIFIED |
| testNobaraRemnant | `build.gradle:71-79` | VERIFIED |
| testNobaraRitual | `build.gradle:81-89` | VERIFIED |
| testVfxCore | `build.gradle:91-99` | VERIFIED |
| testVfxTimeline | `build.gradle:101-109` | VERIFIED |
| testVfxQuality | `build.gradle:111-119` | VERIFIED |
| testVfxAnchor | `build.gradle:121-129` | VERIFIED |
| check depends on all nine current assertion tasks | `build.gradle:131-141` | VERIFIED |
| removed legacy test tasks are absent from `check` | `build.gradle:41-141` | VERIFIED |

## Risks

| Claim | Source | Status |
|---|---|---|
| worktree/checkout divergence can still mislead agents | [[../06-maintenance/Risks-and-tech-debt]] | VERIFIED |
| dual runtime stack risk resolved by deletion | `ProjectSanityTest.java:159-188` | VERIFIED |
| no automated in-game smoke in CI | [[../06-maintenance/Risks-and-tech-debt]] | VERIFIED |

---
tags: #jujutsumod #reference #citations
