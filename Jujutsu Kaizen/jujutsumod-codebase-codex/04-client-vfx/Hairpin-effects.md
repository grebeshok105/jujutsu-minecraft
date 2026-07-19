# Hairpin / Combat Client Effects

<- [[00-MOC]] | [[VFX-core]] | [[../03-systems/Nobara-overview]] | [[../03-systems/Nobara-runtime-flow]]

## Current pipeline

The legacy integer impulse/static-manager path is gone. All transient Nobara combat effects travel through the shared [[VFX-core]] route:

`server ability -> VfxCue -> VfxCuePayload -> VfxDirector -> NobaraVfxRecipes -> director channels`.

| Pipeline | Trigger | Core classes | Status |
|---|---|---|---|
| Shared transient combat VFX | typed server cue | `VfxCue`, `VfxCuePayload`, `VfxDirector`, `NobaraVfxRecipes` | VERIFIED |
| Persistent real nail aura | nail entity render state | `ProjectJjkNailRenderer` + `VfxPalette` | VERIFIED |
| Server combat feedback | authoritative runtime/ritual event | existing server particle families | VERIFIED |

## Nobara recipes

The 25 IDs live in `vfx/NobaraVfxIds.java:7-31` and are all registered by `client/vfx/nobara/NobaraVfxRecipes.java`.

| Scene | IDs | Composition |
|---|---|---|
| Hammer / launch | `hammer`, `impact`, `impact_sound`, `hammer_horizontal`, `hammer_overhead`, `hammer_nail_launch`, `embedded_nail_drive` | anvil/netherite/snap rhythm, camera, HUD |
| Resonance / link | `resonance_channel`, `resonance_strike`, `link_bind`, `detonate` | cursed-energy pulse, ring, local particle burst, chime |
| Enlarge / Boom | `enlarge`, `explosion`, `first_person_snap` | expanding ring/ribbon/blade geometry, shards, sound stack, HUD/camera, hand snap |
| Straw Doll | `remnant_drop`, `ritual_bind`, `doll_strike`, `resonance_release` | trace burst, binding geometry, doll-local puncture, dark-center/cyan-fracture target release |
| Black Flash | `black_flash` | 5 directional blades + 3 lightning variants (forked/spiral/cascade) + expanding shockwave ring + inner blood ring (28 ticks, world-fixed), BF_IMPACT/LIGHTNING/SPARK + directional bursts, 5-layer sound (impact x2 + snap + deep explosion + vortex), 4-impulse camera ~560ms + FOV, HUD flash 250ms + nausea 0.8, blur 300ms, FP snap (caster only), GeckoLib anim. Blood-black palette (core 80/30/30). |
| Self Resonance | `self_resonance` | cursed pulse, self-damage vignette, linked target burst |
| Nail depth | `nail_deepen` | drive-in particle burst, depth ring |
| Nail Trap | `nail_trap_placed`, `nail_trap_armed`, `nail_trap_collapse`, `nail_trap_impact` | placement marker, arm glow, collapse ring, impact burst |

The world layer is registered once in `VfxDirector` on `WorldRenderEvents.AFTER_ENTITIES`; recipes only call its channel. The existing narrow camera and first-person mixins read state from `VfxDirector` rather than owning VFX timelines.

## Real nail aura

`ProjectJjkNailRenderer` remains a state-driven entity renderer, so prepared/flying nails stay attached to their actual entity state. Its compressed-energy envelope uses a narrow rim, tip core, pressure bands, orbiting slivers, and a directional tail. Embedded nails keep only their readable physical state and do not receive the broad envelope. Vanilla soul-fire and the old ignition-tick composition are absent by guard.

## Removed paths

The following old transient paths must not come back:

- `ProjectJjkNobaraImpulsePayload`
- `HairpinWorldRenderer`
- `HairpinCinematicCamera`
- `HairpinScreenOverlay`
- `ResonanceEffects`
- `FpSnapAnimator`

`ProjectSanityTest` guards their absence. Older legacy classes such as `HairpinTimeline`, `HairpinVisualProfile`, `HairpinPlaybackManager`, and fake nail-flight playback remain removed too.

## Constraint

This is Fabric-native composition, not a copied ProjectJJK/Specter/GeckoLib effect system. No external shader library, JSON/DSL, preview scene, or generic bone-effect authoring belongs to V1. The director may invoke its narrow vanilla blur channel, but each scene remains complete when blur is unavailable.

---
tags: #jujutsumod #vfx #hairpin #nobara #verified
