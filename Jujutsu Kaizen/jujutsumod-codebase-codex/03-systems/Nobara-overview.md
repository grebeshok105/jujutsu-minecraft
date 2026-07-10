# Nobara Overview

← [[00-MOC]] · [[Nobara-runtime-flow]] · [[Nail-entity-lifecycle]] · [[Nobara-combat-expansion]] · [[Combat-timing-and-black-flash]] · [[Curse-links]] · [[Straw-Doll-resonance]]

## One-line fantasy

An aggressive Straw Doll kit: prepare durable nails, control their placement with a hammer, convert anchored nails into per-nail Hairpin damage, and use exact timing, curse links, and a remote doll ritual for high-commitment payoffs.

## Canonical runtime

ProjectJJK Nobara is the only active Nobara path. The runtime package is `character/nobara/projectjjk`; removed legacy item/runtime/payload/playback classes are guarded by `ProjectSanityTest.java:159-188`.

| Area | Current contract |
|---|---|
| Nails | persistent `ProjectJjkNailEntity` with typed anchors |
| Hammer | server-routed prepared launch, embedded drive, alternating horizontal/overhead attacks |
| Hairpin | R Enlarge and B Boom operate on concrete owned nails |
| Black Flash | second hammer input during the server window amplifies the eligible impact and grants synced focus |
| Curse links | explicit server-owned links gate self resonance; no status effect creates one |
| Doll Resonance | remnant + hammer + doll + nail ritual remains a separate remote attack |
| Presentation | transient actions use `VfxCue -> VfxDirector -> NobaraVfxRecipes`; persistent nails use `ProjectJjkNailRenderer` |

## Input map

| Input | Client request | Server result |
|---|---|---|
| Hold nail item | vanilla item use | prepares one nail per 10 ticks, cap eight |
| LMB with Nobara hammer | `HAMMER_CONTEXT` | consume Black Flash window or choose prepared launch, embedded drive, horizontal, or overhead attack |
| R | `HAIRPIN_ENLARGE` | delayed per-nail Enlarge against the looked-at marked target |
| B | `HAIRPIN_EXPLOSION` | staged per-nail Boom across valid owned nails |
| Shift+R | `SELF_RESONANCE` | use the sole link or request selection when multiple links exist |
| Shift+R after selection | same action | starts self-resonance windup if the selected link remains valid |

All requests are revalidated on the logical server. **Source:** `JujutsuKeybinds.java:21-70`, `ProjectJjkNobaraActions.java:11-37`, `NobaraHammerCombatRuntime.java:36-93`. **Status:** VERIFIED.

## Initial balance constants

These are implementation tuning values, not canon claims: Enlarge `4` per nail, Boom `3` per nail, Straw Doll Resonance `28`, self resonance `6/18`, Black Flash multiplier `1.75`, horizontal hammer `5`, overhead hammer `8`, and embedded drive `4`.

**Source:** `ProjectJjkNobaraProfile.java:27-58`. **Status:** VERIFIED; manual balance quality remains UNKNOWN.

## Items and visual ownership

`hairpin_nail`, `straw_doll_hammer`, `straw_doll`, and `resonance_remnant` remain the product item IDs. The starter loadout supplies hammer, doll, and nails but never a remnant. Default nail/hammer items point to the ProjectJJK runtime classes; the doll is original mod content.

See [[../04-client-vfx/VFX-core]] for transient VFX ownership and [[../05-reference/Public-api-surface]] for supported extension points.

---
tags: #jujutsumod #nobara #projectjjk #verified
