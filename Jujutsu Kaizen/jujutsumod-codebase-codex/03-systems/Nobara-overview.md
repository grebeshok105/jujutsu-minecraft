# Nobara Overview

← [[00-MOC]] · detail: [[Nobara-runtime-flow]] · [[Nail-entity-lifecycle]] · [[Target-marks-and-resonance]] · [[Straw-Doll-resonance]]

## One-line fantasy

Canon-forward Straw Doll kit: charge real nail entities → strike with hammer → embed/detonate nails, or earn a target remnant and complete a doll ritual for remote Resonance.

## Canonical implementation

The old jujutsumod cinematic Nobara stack has been removed. ProjectJJK Nobara is now the only runtime path.

| Area | Current source | Status |
|---|---|---|
| Runtime package | `src/main/java/jujutsu/mod/character/nobara/projectjjk/` | VERIFIED |
| Main item ids | `hairpin_nail`, `straw_doll_hammer`, `straw_doll`, `resonance_remnant` | VERIFIED |
| Item classes | `ProjectJjkNailItem`, `ProjectJjkHammerItem`, `ProjectJjkStrawDollItem` | VERIFIED |
| Resonance runtime | `ProjectJjkStrawDollRuntime` + `ProjectJjkRitualPolicy` | VERIFIED |
| Network payloads | `VfxCuePayload`, `NobaraActionPayload`, character selection sync/select | VERIFIED |
| Removed legacy classes | `NobaraHairpinRuntime`, `NobaraCombatStateManager`, `HairpinGameplayService`, legacy Hairpin payloads/playback | VERIFIED |

Source: `src/test/java/jujutsu/mod/ProjectSanityTest.java:159-188`.

## Combat loop

```mermaid
flowchart LR
  A[Hold nail item] --> B[prepareNails entities]
  B --> C[Use hammer]
  C --> D{launch mode}
  D -->|RMB| E[piercing nails]
  D -->|LMB action| F[explosive nails]
  E --> G[impact embed + mark]
  F --> H[impact boom + disappear]
  G --> I[R Enlarge / B Boom]
  E --> J{2nd ordinary hit<br/>same caster + target}
  J --> K[target-bound remnant drops]
  K --> L[pick up remnant]
  L --> M[hammer main hand + doll offhand + nail]
  M --> N[Shift + RMB: 14-tick ritual]
  N --> O[remote Resonance<br/>same dimension, loaded, <=64 blocks]
```

Hairpin and Resonance are separate mechanics. Marks still feed Hairpin/target pressure; they no longer substitute for the canon-defining remnant/effigy ritual. See [[Straw-Doll-resonance]].

## Items

| Item id | Behavior class | Source | Status |
|---|---|---|---|
| `hairpin_nail` | `ProjectJjkNailItem` | `JujutsuItems.java:14` | VERIFIED |
| `straw_doll_hammer` | `ProjectJjkHammerItem` | `JujutsuItems.java:15` | VERIFIED |
| `projectjjk_hairpin_nail` | alias to same ProjectJJK item class | `JujutsuItems.java:16` | VERIFIED |
| `projectjjk_straw_doll_hammer` | alias to same ProjectJJK item class | `JujutsuItems.java:17` | VERIFIED |
| `resonance_remnant` | target-bound ritual resource | `JujutsuItems.java:18`; `ProjectJjkResonanceRemnant.java:14-28` | VERIFIED |
| `straw_doll` | reusable animated ritual effigy | `JujutsuItems.java:19`; `ProjectJjkStrawDollItem.java:19-69` | VERIFIED |

Default item definitions render with the ProjectJJK models, not the removed legacy item models. Source: `ProjectSanityTest.java:195-199`.

The Nobara starter loadout now includes the reusable straw doll. Remnants are earned through combat and are not granted as starter inventory.

## Current balance note

Current R/B finisher damage is tuned for vanilla smoke testing rather than strict ProjectJJK parity:

- Hairpin Enlarge: `16.0f`
- Hairpin Explosion / Boom: `12.0f` fixed, no mark scaling

Source: `ProjectSanityTest.java:229-234`; implementation source `ProjectJjkNobaraProfile.java`.

## Removed legacy stack

The following were removed in the cleanup pass:

- Legacy runtime/items: `NobaraHairpinRuntime`, `NobaraCombatStateManager`, `HairpinGameplayService`, `HairpinNailItem`, `StrawDollHammerItem`
- Legacy networking: removed `ProjectJjkNobaraImpulsePayload`, `HairpinFxPayload`, `HairpinNailFlightPayload`, and `PreparedNailsPayload`
- Removed legacy client playback: `HairpinPlayback`, `HairpinPlaybackManager`, `NobaraNailFlightManager`, `HairpinTimeline`, `HairpinVisualProfile`
- Legacy assets: old item models/textures and old unused Hairpin post-shaders

Regression guard: `ProjectSanityTest.java:159-188`.

---
tags: #jujutsumod #nobara #projectjjk #verified
