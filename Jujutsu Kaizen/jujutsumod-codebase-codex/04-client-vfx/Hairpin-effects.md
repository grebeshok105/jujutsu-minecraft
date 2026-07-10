# Hairpin / Combat Client Effects

← [[00-MOC]] · [[VFX-core]] · [[../03-systems/Nobara-overview]] · [[../03-systems/Nobara-runtime-flow]]

## Current pipeline

The legacy integer impulse/static-manager path is gone. All transient Nobara combat effects travel through the shared [[VFX-core]] route:

`server ability → VfxCue → VfxCuePayload → VfxDirector → NobaraVfxRecipes → director channels`.

| Pipeline | Trigger | Core classes | Status |
|---|---|---|---|
| Shared transient combat VFX | typed server cue | `VfxCue`, `VfxCuePayload`, `VfxDirector`, `NobaraVfxRecipes` | VERIFIED |
| Persistent real nail aura | nail entity render state | `ProjectJjkNailRenderer` + `VfxPalette` | VERIFIED |
| Server combat feedback | authoritative runtime/ritual event | existing server particle families | VERIFIED |

## Nobara recipes

The ten central IDs live in `vfx/NobaraVfxIds.java:6-15` and are all registered by `client/vfx/nobara/NobaraVfxRecipes.java:23-33`.

| Scene | IDs | Composition |
|---|---|---|
| Hammer / launch | `hammer`, `impact`, `impact_sound` | anvil/netherite/snap rhythm, camera, HUD |
| Resonance / link | `resonance_channel`, `resonance_strike`, `link_bind`, `detonate` | cursed-energy pulse, ring, local particle burst, chime |
| Enlarge / Boom | `enlarge`, `explosion`, `first_person_snap` | expanding ring/ribbon/blade geometry, shards, sound stack, HUD/camera, hand snap |

The world layer is registered once in `VfxDirector.java:44` on `WorldRenderEvents.AFTER_ENTITIES`; recipes only call its channel. The existing narrow camera and first-person mixins read state from `VfxDirector` rather than owning VFX timelines.

## Real nail aura

`ProjectJjkNailRenderer` remains a state-driven entity renderer, so prepared/flying nails stay attached to their actual entity state. It now consumes the core cursed-energy palette (`ProjectJjkNailRenderer.java:23,31-42`; `VfxPalette.java`) rather than duplicating cyan/white values. It is not a director recipe.

## Removed paths

The following old transient paths must not come back:

- `ProjectJjkNobaraImpulsePayload`
- `HairpinWorldRenderer`
- `HairpinCinematicCamera`
- `HairpinScreenOverlay`
- `ResonanceEffects`
- `FpSnapAnimator`

`ProjectSanityTest.java:357-362` guards their absence. Older legacy classes such as `HairpinTimeline`, `HairpinVisualProfile`, `HairpinPlaybackManager`, and fake nail-flight playback remain removed too.

## Constraint

This is Fabric-native composition, not a copied ProjectJJK/Specter/GeckoLib effect system. No external shader library, JSON/DSL, preview scene, or generic bone-effect authoring belongs to V1.

---
tags: #jujutsumod #vfx #hairpin #nobara #verified
