# Specter Verdict

← [[00-MOC]] · [[02-architecture/Libraries]]

## Short answer

**Specter is NOT required to reimplement ProjectJJK ability gameplay.**

## Evidence

1. Ability cast path is: slots → CE/CD checks → `Ability.onCast` → entities/movesets/damage.
2. Specter appears as `net.lib.Specter.*` for particle behaviors and client shader init — **presentation**.
3. Declared `specter-v0.1.0.jar` is **missing** under `META-INF/jars`; code is embedded, not a clean external API you must depend on.
4. Core combat numbers and flows live in ProjectJJK packages, not Specter.

## What actually backs presentation

| Need | Lib |
|---|---|
| Post shaders / shake / B&W flash | **Satin** (`SatinUtil`) |
| 3D geo models/animations | **GeckoLib** |
| Spirit/NPC AI | **SmartBrainLib** |
| Config screen | **MidnightLib** |
| Boss bars | **BossBarLib** |
| Particles helpers | Specter embedded |

## For jujutsumod (1.21.8)

| Approach | Recommendation |
|---|---|
| Gameplay reimpl | Pure Fabric API — **no Specter** |
| Screen FX | Your GLSL / optional Satin port |
| 3D nails | Optional GeckoLib **1.21.8** build — not 1.21.1 jar |
| Outline marks | Own sync + render; don't copy mixins blindly |

## Legal

Specter + ProjectJJK are ARR-ish / ARR. Reimplement, don't vendor decompiled Specter.

---

tags: #projectjjk #specter #jujutsumod
