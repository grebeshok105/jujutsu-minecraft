# Hairpin / Combat Client Effects

← [[00-MOC]]

## Two pipelines

| Pipeline | Trigger | Core classes | Status |
|---|---|---|---|
| Cinematic Hairpin FX | `HairpinFxPayload` / commands | `HairpinPlayback`, `HairpinTimeline`, `HairpinVisualProfile`, particles, world renderer, screen overlay | VERIFIED exists |
| ProjectJJK combat impulses | `ProjectJjkNobaraImpulsePayload` | `JujutsuClientNetworking` handlers | VERIFIED |

## Timeline model (shared)

**Source:** `src/main/java/jujutsu/mod/fx/HairpinTimeline.java`  
Phases via `phaseAt` / game-time helpers (`:21-93`).  
Tests: `HairpinTimelineTest` task `testHairpinTimeline`.  
**Status:** VERIFIED

Visual budgets: `HairpinVisualProfile` + `testHairpinVisualProfile`.

## Screen overlay

**Source:** `HairpinScreenOverlay.register`  
Flash on snap/bloom phases (Fabric HudElementRegistry — design notes).  
**Status:** VERIFIED register site

## World renderer

**Source:** `HairpinWorldRenderer.register` `:52`  
Fracture/ribbon geometry after entities.  
**Status:** VERIFIED register

## Camera

Mixins: `HairpinCameraMixin`, `HairpinGameRendererMixin` + `HairpinCinematicCamera`.  
**Source:** `jujutsumod.client.mixins.json`  
**Status:** VERIFIED listed

## Impulse handling (combat)

**Source:** `JujutsuClientNetworking.java`

| Handler | Line | Role |
|---|---:|---|
| `handleProjectJjkImpulse` | 64 | dispatch by kind |
| `handleProjectJjkImpact` | 116 | impact VFX/SFX |
| `handleResonanceStrike` | 127 | resonance |
| `handleHairpinEnlarge` | 139 | enlarge |
| `handleHairpinExplosion` | 153 | explosion |
| `playNoFalloff` | 176 | distance-independent SFX helper |

**Status:** VERIFIED method map

## Sounds

Hairpin + projectjjk sound events in `JujutsuSounds` + `sounds.json`.  
**Status:** VERIFIED registry

## Shaders

Assets: `shaders/include/hairpin_*.glsl`, `shaders/post/hairpin_*.fsh/vsh`.  
Whether always bound in pipeline: **UNKNOWN** (see Uncertainties).

---
tags: #jujutsumod #vfx #hairpin
