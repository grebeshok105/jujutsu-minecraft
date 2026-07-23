# Nobara Hairpin Depth, Chain, Trap, Remnants, and Momentum Design

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

## Goal

Extend the canonical ProjectJJK Nobara slice with readable directed and mass Hairpin identities, sequential per-nail detonations, three-level embedded depth, a territory-control nail trap, deterministic Black Flash debugging, richer Bound Remnant art, and a one-minute reward after Straw Doll Resonance.

## Controls

- `R`: directed Hairpin activation. It starts from the looked-at owned nail or from owned nails embedded in the looked-at entity, then includes eligible nails within roughly 10 blocks of that seed. The chain advances every 2 ticks.
- `Shift+R`: existing Self Resonance. It is not changed by this feature.
- `B`: mass Hairpin detonation over all currently available loaded owned nails, ordered by stable nearest-neighbor traversal. The chain advances every 3 ticks.
- `Shift+B`: places a triangular nail trap at the looked-at ground position, up to 8 blocks away.

R and B are server-authoritative requests. The server repeats character, ownership, anchor, distance, loaded-state, and action-state validation.

## Directed and Mass Hairpin

Every chain stores immutable nail UUIDs and its own cursor. A missing nail is skipped only when final removal is confirmed; temporary unavailability pauses that entry rather than consuming the rest of the chain. Every successful step produces one Hairpin damage event, one sound beat, and one `VfxCue` before consuming that nail.

`R` deals base `5` damage per nail before depth and Momentum multipliers. Entity damage and its roughly 10-block selection radius are calculated by ProjectJJK combat logic, never by vanilla explosion damage. A detonated block-anchor additionally creates explosion power `1.5` only to destroy nearby blocks. This terrain explosion respects normal server explosion hooks and protection. It does not damage entities.

`B` deals base `3` damage per nail before multipliers, does not destroy blocks, and visits every available loaded owned nail. Ordering begins with the nail nearest the caster and repeatedly selects the nearest remaining nail; UUID is the final tie-breaker so equal-distance chains remain deterministic.

The final successful explosion in either chain gets a stronger sound and VFX cue, not extra damage. If later entries disappear, the final surviving successful entry receives the finale flag.

## Embedded Depth

Each embedded nail stores and synchronizes depth `1..3`. New nails begin at I. Any successful ordinary hammer hit against a carrier containing the attacking Nobara's embedded nails deepens exactly one nail: greatest current depth first, then oldest nail, then UUID. This does not require aiming at the nail and does not use a separate hammer animation.

Depth multipliers are `1.0`, `1.35`, and `1.75`. They affect R and B damage for that nail. Transition to II and III emits a compact cursed compression/metal/blood-black VFX cue. The renderer offsets the nail farther along its stored local-forward vector at each level. A level III detonation uses a dedicated heavy recipe in addition to the ordinary chain/finale flags.

Depth persists in entity save data and is available to the client through synchronized entity data. Values are clamped on load.

## Nail Trap

`Shift+B` consumes three nail items and places three owned block-anchored nail entities as a triangle of radius 6 blocks around a ground point within 8 blocks. The server validates replaceable/supported placement positions. Only one trap may exist per owner; placing a new one safely removes/refunds no previous nails.

The trap lasts 30 seconds and triggers once when the first valid enemy enters its triangular prism. Over 6 ticks its three nails launch visually toward the trigger center. It deals 15 total Hairpin damage, applies the shared action interrupt for 12 ticks without a vanilla Slowness effect, and embeds one new ordinary depth-I nail into the target. The three trap nails are consumed. Unload pauses resolution; confirmed removal clears the trap.

## Forced Black Flash Debugging

`/jujutsu debug black_flash_force true|false` toggles an in-memory per-player server flag. While enabled, every supported physical Black Flash source succeeds without timing input: hammer horizontal/overhead impacts, prepared-nail hammer launch, initial nail embed, and hammer hits against a carrier with embedded nails. Hairpin R/B/trap technique damage is not itself a Black Flash source. State clears on disconnect and server stop, and the command reports the current value.

## Bound Remnant Visual Types

`ProjectJjkResonanceRemnant` gains a persisted `RemnantVisualType`: `FLESH`, `TOKEN`, or `CURSE`.

- `FLESH`: animals and organic non-humanoids; flesh/fur fragment.
- `TOKEN`: players, villagers, golems, and the safe fallback; cloth/hair/material token.
- `CURSE`: entities in `jujutsumod:resonance_remnant_curse`; dark cursed matter.

All three are original 64x64 pixel-art item textures with transparent backgrounds, directional lighting, cast shadows, readable silhouettes, and a common straw binding. Item model selection is data-driven from the stored visual type. Existing saved remnants without a type decode as `TOKEN`.

## Resonant Momentum

A successful Straw Doll Resonance grants its caster `Resonant Momentum` for 60 seconds. It is a dedicated server state, not a potion effect. While active:

- nail preparation and prepared-nail launch timing are 15% faster;
- hammer, directed Hairpin, and mass Hairpin damage are multiplied by `1.15`;
- the HUD displays a localized icon/timer.

Reapplication refreshes duration; it does not stack. Disconnect/server stop clears transient state. Damage modification is performed at explicit Nobara damage sites rather than by a global damage hook.

## Balance and VFX

All timings, ranges, damage, depth offsets/multipliers, trap values, Momentum values, and chain cadences live in `ProjectJjkNobaraProfile`. Every transient visual uses `VfxCue -> VfxDirector -> NobaraVfxRecipes`. New cue metadata must encode depth/finale without making the client authoritative.

## Acceptance Criteria

- R and B have distinct selection, damage, cadence, and terrain behavior.
- Chains are deterministic, visibly sequential, and issue one damage/sound/VFX event per nail.
- Finale changes presentation only.
- Depth survives reload, changes renderer position, scales damage, and emits transition/level-III VFX.
- Shift+B trap has 6-block triangle radius, 15 total damage, 12-tick interrupt, 30-second life, and embeds one normal nail.
- Forced Black Flash remains enabled until explicitly disabled, disconnect, or server stop.
- Three remnant variants render at 64x64 and old data falls back safely.
- Straw Doll Resonance grants a visible, non-stacking 60-second `1.15x` Momentum state.
- `check`, production build, `git diff --check`, runtime JAR install, and SHA-256 comparison pass.

