# Claim -> Source Index (jujutsumod)

<- [[00-MOC]] | standard: [[01-meta/Citation-standard]]

**Default path prefix:** repository root (main branch).
All sources relative to project root unless noted.

---

## Mod identity / versions

| Claim | Source | Status |
|---|---|---|
| mod id `jujutsumod` | `src/main/java/jujutsu/mod/JujutsuMod.java:29` | VERIFIED |
| MC 1.21.8 | `gradle.properties:10` | VERIFIED |
| Java >=21 | `src/main/resources/fabric.mod.json` | VERIFIED |
| mod version 1.0.0 | `gradle.properties:15` | VERIFIED |

## Entrypoints

| Claim | Source | Status |
|---|---|---|
| main entry | `fabric.mod.json` entrypoints.main | VERIFIED |
| client entry | `fabric.mod.json` entrypoints.client | VERIFIED |
| init order: entities -> data components -> items -> particles -> sounds -> effects -> networking -> ritual -> straw doll -> anchor lifecycle -> hammer -> action guard -> self resonance -> nail trap -> curse link cleanup -> commands -> forced black flash | `JujutsuMod.java:33-51` | VERIFIED |
| client registers Straw Doll renderer factory, nail renderer, particle factories, VfxDirector, Nobara recipes (25 IDs), client receivers, keybinds | `JujutsuModClient.java:17-25` | VERIFIED |
| removed legacy `HairpinPlaybackManager` / `NobaraNailFlightManager` are not initialized | guard `ProjectSanityTest` | VERIFIED |

## Registries - items

| Claim | Source | Status |
|---|---|---|
| default `hairpin_nail` uses `ProjectJjkNailItem` | `JujutsuItems.java:19` | VERIFIED |
| default `straw_doll_hammer` uses `ProjectJjkHammerItem` | `JujutsuItems.java:20` | VERIFIED |
| ProjectJJK alias items use the same ProjectJJK classes | `JujutsuItems.java:21-22` | VERIFIED |
| `resonance_remnant` is a non-stackable typed target-link item | `JujutsuItems.java:23` | VERIFIED |
| `straw_doll` is a non-stackable reusable GeckoLib item | `JujutsuItems.java:24` | VERIFIED |
| Nobara starter tools include hammer, doll, and up to 16 nails but no remnant | `ProjectJjkNobaraLoadout.java` | VERIFIED |
| `resonance_target` and `resonance_remnant_visual` persist and network-sync | `JujutsuDataComponents.java:11-20` | VERIFIED |

## Registries - entities / particles / sounds / effects

| Claim | Source | Status |
|---|---|---|
| entity `projectjjk_nail` | `JujutsuEntities.java` | VERIFIED |
| 8 Hairpin particle types remain for ProjectJJK runtime/VFX | `JujutsuParticles.java` | VERIFIED |
| hairpin + projectjjk sound events remain | `JujutsuSounds.java` | VERIFIED |
| mob effect `resonant_momentum` (BENEFICIAL, 0x55D6DC) | `JujutsuEffects.java:11-14` | VERIFIED |

## Networking payloads

| Claim | Source | Status |
|---|---|---|
| S2C typed VFX cue registered | `JujutsuNetworking.java:18` | VERIFIED |
| S2C character selection sync registered | `JujutsuNetworking.java:19` | VERIFIED |
| C2S character select registered | `JujutsuNetworking.java:20` | VERIFIED |
| C2S Nobara action registered | `JujutsuNetworking.java:21` | VERIFIED |
| S2C CurseLink options registered | `JujutsuNetworking.java:22` | VERIFIED |
| C2S CurseLink selection registered | `JujutsuNetworking.java:23` | VERIFIED |
| S2C Black Flash focus registered | `JujutsuNetworking.java:24` | VERIFIED |
| server handles Nobara action through `ProjectJjkNobaraActions.tryCast` | `JujutsuNetworking.java:31-32` | VERIFIED |
| selected CurseLink is revalidated by `SelfResonanceRuntime.select` | `JujutsuNetworking.java:33-34` | VERIFIED |
| client receives VFX, character selection, CurseLink menu options, and focus mirror | `JujutsuClientNetworking.java:17-27` | VERIFIED |
| client disconnect clears selection and focus caches | `JujutsuClientNetworking.java:26` | VERIFIED |
| removed legacy S2C VFX payloads are guarded against re-registration | `ProjectSanityTest` | VERIFIED |
| VFX cue broadcast uses radius filtering and `canSend` | `JujutsuNetworking.java:43-57` | VERIFIED |
| `VfxCue` carries immutable origin fallback, optional anchor ID, world-space `anchorOffset`, and normalized `direction` (8-field record) | `VfxCue.java`; test `VfxCueTest` | VERIFIED |

## Character selection

| Claim | Source | Status |
|---|---|---|
| NONE/NOBARA enum | `JujutsuCharacter.java` | VERIFIED |
| select + loadout | `CharacterSelectionManager.java` | VERIFIED |
| keybind register | `JujutsuKeybinds.java` | VERIFIED |
| 7 client mixins: skin/camera/game renderer/delta tracker/first-person/living entity renderer/player renderer | `jujutsumod.client.mixins.json` | VERIFIED |

## Nobara runtime

| Claim | Source | Status |
|---|---|---|
| canonical runtime package is `character/nobara/projectjjk` | `src/main/java/jujutsu/mod/character/nobara/projectjjk/` | VERIFIED |
| old runtime classes removed | `ProjectSanityTest` | VERIFIED |
| old legacy payload/playback classes removed | `ProjectSanityTest` | VERIFIED |
| prepared nails are persistent typed-anchor entities with entity/block/runtime-object save data | `ProjectJjkNailEntity.java`; `NailAnchor.java` | VERIFIED |
| entity anchors preserve UUID identity through temporary unload and discard only after confirmed removal | `NailAnchorLifecycle.java`; `NailAnchorTest.java` | VERIFIED |
| contextual hammer is server-routed and suppresses the vanilla second hit | `NobaraHammerCombatRuntime.java`; `NobaraActionGuard.java` | VERIFIED |
| Black Flash is a server-window second input, applies only bonus/amplification, and grants synced focus | `BlackFlashWindow.java`; `NobaraHammerCombatRuntime.java`; `BlackFlashFocus.java` | VERIFIED |
| CurseLinks are explicit source-owned server records; self resonance revalidates selection and self-hit before propagation | `CurseLink.java`; `CurseLinkRegistry.java`; `SelfResonanceRuntime.java` | VERIFIED |
| nail trap places 3 nails in a triangle, arms, collapses on proximity, deals 15 damage | `NailTrapRuntime.java`; `NailTrap.java`; `ProjectJjkNobaraProfile.java:41-48` | VERIFIED |
| ProjectJJK prepared/flying nail aura is rendered by the real entity renderer | `ProjectJjkNailRenderer.java` | VERIFIED |
| transient Nobara scenes use registered VFX recipes; removed `HairpinWorldRenderer` is not a live path | `NobaraVfxRecipes.java`; guard `ProjectSanityTest` | VERIFIED |

## Straw Doll Resonance

| Claim | Source | Status |
|---|---|---|
| every second accepted ordinary nail damage hit for one caster/target pair drops a target-bound remnant at the wound | `ProjectJjkNobaraRuntime.java`; `ProjectJjkRitualPolicy.java`; `ProjectJjkStrawDollRuntime.java`; `ProjectJjkRemnantProgress.java` | VERIFIED |
| Resonance start requires main-hand hammer, offhand doll, matching remnant, nail, alive loaded target, same dimension, finite distance <=64, and no duplicate cast | `ProjectJjkStrawDollRuntime.java`; `ProjectJjkRitualPolicy.java` | VERIFIED |
| the 14-tick ritual revalidates requirements every server tick and does not require line of sight | `ProjectJjkStrawDollRuntime.java` | VERIFIED |
| successful Resonance damages/staggers target, consumes marks, discards owned embedded nails, clears glow, and emits caster/target cues | `ProjectJjkStrawDollRuntime.java` | VERIFIED |
| disconnect/caster death/target-death-or-unload/server stop clear relevant pending/progress state | `ProjectJjkStrawDollRuntime.java` | VERIFIED |
| all Straw Doll transient particles/sounds use VFX Core cues | `ProjectJjkStrawDollRuntime.java`; guard `ProjectSanityTest` | VERIFIED |

## Nobara profile numbers

| Claim | Source | Status |
|---|---|---|
| hold timing adds one nail per 10 ticks after first nail | `ProjectJjkNobaraProfile.java:6`; test `ProjectJjkNobaraProfileTest` | VERIFIED |
| max nail age 1200 | `ProjectJjkNobaraProfile.java:10` | VERIFIED |
| launch delay 4 ticks per nail index | `ProjectJjkNobaraProfile.java:11` | VERIFIED |
| launch range around prepared nails is 2 blocks | `ProjectJjkNobaraProfile.java:17` | VERIFIED |
| Enlarge damage 4 per nail | `ProjectJjkNobaraProfile.java:64` | VERIFIED |
| Boom base damage 3 per nail | `ProjectJjkNobaraProfile.java:32` | VERIFIED |
| Directed damage 5 per nail, chain radius 10 | `ProjectJjkNobaraProfile.java:33-34` | VERIFIED |
| Nail trap damage 15, radius 6, placement range 8, lifetime 600 | `ProjectJjkNobaraProfile.java:41-48` | VERIFIED |
| Nail depth multipliers 1.0/1.35/1.75 | `ProjectJjkNobaraProfile.java:38-40` | VERIFIED |
| doll Resonance 28 | `ProjectJjkNobaraProfile.java:68` | VERIFIED |
| self resonance 6/18 | `ProjectJjkNobaraProfile.java:71-72` | VERIFIED |
| Black Flash multiplier 1.75 | `ProjectJjkNobaraProfile.java:73` | VERIFIED |
| horizontal hammer 5, overhead hammer 8, embedded drive 4 | `ProjectJjkNobaraProfile.java:74-76` | VERIFIED |
| resonant momentum 1.15x for 1200 ticks | `ProjectJjkNobaraProfile.java:49-50` | VERIFIED |

## Client VFX

| Claim | Source | Status |
|---|---|---|
| one `VfxCuePayload` receiver delegates to `VfxDirector`; it has no effect-ID switch | `JujutsuClientNetworking.java:18-19` | VERIFIED |
| all 25 Nobara IDs register Java recipes | `NobaraVfxIds.java:7-31`; `NobaraVfxRecipes.java` | VERIFIED |
| director owns world/HUD callbacks, tick, unknown-ID safety, a 64-instance bound, `ClientLevel` identity cleanup, and null/disconnect reset | `VfxDirector.java` | VERIFIED |
| non-expired late cues receive actual `initialAgeTicks`; one-shot opening beats run only below two ticks; all 39 timed Nobara channel calls preserve age | `VfxTimeline.java`; `NobaraVfxRecipes.java`; guard `ProjectSanityTest` | VERIFIED |
| live world anchors resolve as `anchor.position() + anchorOffset`; missing anchors fall back to immutable `cue.origin()` | `VfxAnchorResolver.java`; test `VfxAnchorResolverTest` | VERIFIED |
| no-falloff SFX is a director channel | `VfxSoundChannel.java` | VERIFIED |
| removed overlay/world/camera/playback managers are guarded as absent | `ProjectSanityTest` | VERIFIED |
| first-person snap uses an age-aware `VfxFirstPersonChannel` start, lasts 0.75 seconds, and traverses the full 0..15 phase | `VfxFirstPersonChannel.java`; `NobaraFirstPersonSnapMixin.java` | VERIFIED |
| director-owned post-process calls public vanilla blur and disables only blur for the session on runtime/linkage failure | `VfxPostProcessChannel.java` | VERIFIED |

## Assets

| Claim | Source | Status |
|---|---|---|
| 8 particle json | `assets/jujutsumod/particles/` | VERIFIED |
| runtime item models include ProjectJJK nail/hammer and original Straw Doll/remnant resources | `ProjectSanityTest` | VERIFIED |
| original Straw Doll source/runtime set includes matching cubes, animation tracks, portable texture path, bounded 64x64 Box UVs | `source-assets/blockbench/straw_doll.bbmodel`; `geckolib/models/straw_doll.geo.json`; `geckolib/animations/straw_doll.animation.json` | VERIFIED |
| old unused Hairpin post-shaders removed | `ProjectSanityTest` | VERIFIED |

## Tests / build

| Claim | Source | Status |
|---|---|---|
| 18 custom test tasks wired into `check` | `build.gradle` | VERIFIED |
| testProjectSanity covers legacy absence, registration, VFX wiring, assets | `ProjectSanityTest.java` | VERIFIED |
| Nail-anchor, Black Flash-window, CurseLink-registry, HairpinChain, NailTrap, ResonantMomentum, ServerTimeDilation, VfxTime, RemnantVisualType assertions are wired | `build.gradle` | VERIFIED |
| removed legacy test tasks are absent from `check` | `build.gradle` | VERIFIED |

## Risks

| Claim | Source | Status |
|---|---|---|
| worktree/checkout divergence resolved: main now contains cinematic branch | git log main | VERIFIED (resolved 2026-07-19) |
| dual runtime stack risk resolved by deletion | `ProjectSanityTest` | VERIFIED |
| no automated in-game smoke in CI | [[../06-maintenance/Risks-and-tech-debt]] | VERIFIED |

---
tags: #jujutsumod #reference #citations
