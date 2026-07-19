# Nobara Overview

<- [[00-MOC]] | [[Nobara-runtime-flow]] | [[Nail-entity-lifecycle]] | [[Nobara-combat-expansion]] | [[Combat-timing-and-black-flash]] | [[Curse-links]] | [[Straw-Doll-resonance]]

## One-line fantasy

An aggressive Straw Doll kit: prepare durable nails, control their placement with a hammer, convert anchored nails into per-nail Hairpin damage, and use exact timing, curse links, and a remote doll ritual for high-commitment payoffs.

## Canonical runtime

ProjectJJK Nobara is the only active Nobara path. The runtime package is `character/nobara/projectjjk`; removed legacy item/runtime/payload/playback classes are guarded by `ProjectSanityTest`.

| Area | Current contract |
|---|---|
| Nails | persistent `ProjectJjkNailEntity` with typed anchors |
| Hammer | server-routed prepared launch, embedded drive, alternating horizontal/overhead attacks |
| Hairpin | R Directed (Enlarge) and B Mass (Boom) operate on concrete owned nails |
| Nail Trap | Shift+B places a triangular 3-nail trap that arms and collapses on proximity |
| Black Flash | second hammer input during the server window amplifies the eligible impact and grants synced focus |
| Curse links | explicit server-owned links gate self resonance; no status effect creates one |
| Doll Resonance | remnant + hammer + doll + nail ritual remains a separate remote attack |
| Presentation | transient actions use `VfxCue -> VfxDirector -> NobaraVfxRecipes`; persistent nails use `ProjectJjkNailRenderer` |

## Input map

| Input | Client request | Server result |
|---|---|---|
| Hold nail item | vanilla item use | prepares one nail per 10 ticks, cap eight |
| LMB with Nobara hammer | `HAMMER_CONTEXT` (action 2) | consume Black Flash window or choose prepared launch, embedded drive, horizontal, or overhead attack |
| R | `HAIRPIN_DIRECTED` (action 0) | delayed per-nail Enlarge against the looked-at marked target |
| B | `HAIRPIN_MASS` (action 1) | staged per-nail Boom across valid owned nails via `HairpinChainScheduler` |
| Shift+R | `SELF_RESONANCE` (action 3) | use the sole link or request selection when multiple links exist |
| Shift+B | `NAIL_TRAP` (action 4) | place a triangular nail trap at the aimed ground position |

Deprecated aliases: `HAIRPIN_ENLARGE = HAIRPIN_DIRECTED`, `HAIRPIN_EXPLOSION = HAIRPIN_MASS` (`ProjectJjkNobaraActions.java:16-17`).

All requests are revalidated on the logical server. **Source:** `JujutsuKeybinds.java`, `ProjectJjkNobaraActions.java:21-41`, `NobaraHammerCombatRuntime.java`. **Status:** VERIFIED.

## Balance constants

These are implementation tuning values, not canon claims.

| Parameter | Value | Source line |
|---|---|---|
| Enlarge damage per nail | 4.0 | `:64` |
| Boom damage per nail | 3.0 | `:32` |
| Directed damage per nail | 5.0 | `:33` |
| Directed chain radius | 10.0 | `:34` |
| Nail trap damage | 15.0 | `:46` |
| Nail depth multipliers | 1.0 / 1.35 / 1.75 | `:38-40` |
| Straw Doll Resonance | 28.0 | `:68` |
| Self resonance (self / linked) | 6.0 / 18.0 | `:71-72` |
| Black Flash multiplier | 1.75 | `:73` |
| Horizontal hammer | 5.0 | `:74` |
| Overhead hammer | 8.0 | `:75` |
| Embedded nail drive | 4.0 | `:76` |
| Resonant momentum (multiplier / duration) | 1.15x / 1200 ticks | `:49-50` |

**Source:** `ProjectJjkNobaraProfile.java:4-96`. **Status:** VERIFIED; manual balance quality remains UNKNOWN.

## Items and visual ownership

`hairpin_nail`, `straw_doll_hammer`, `straw_doll`, and `resonance_remnant` remain the product item IDs. The starter loadout supplies hammer, doll, and nails but never a remnant. Default nail/hammer items point to the ProjectJJK runtime classes; the doll is original mod content.

See [[../04-client-vfx/VFX-core]] for transient VFX ownership and [[../05-reference/Public-api-surface]] for supported extension points.

---
tags: #jujutsumod #nobara #projectjjk #verified
