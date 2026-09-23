# Straw Doll Resonance

Status: CURRENT

Ordinary embedded nails provide anchor/depth setup and target marks; they do not mint a bound remnant. An overhead hammer action extracts a bound remnant only when the target is deeply anchored — at least one live depth-3 anchor for that target — and extraction itself does not consume the setup. The ritual requires a matching remnant, nail, doll, valid target, same dimension, range, and no duplicate pending cast. Final release revalidates before consuming resources and applying 28 damage.

A successful ritual follows the authored 40-tick timeline: bind at t0, windup at t10, doll strike at t24, and release at t30. The pre-release beats are presentation-only; release consumes one nail and one bound remnant, then applies damage, momentum, stagger, and the release VFX. Resonance hit-stop is presentation-only (camera impulse, sound duck, post-process blur, and timeline cues); it never mutates the server tick rate.

DOLL_STRIKE and RESONANCE_RELEASE remain transient VFX Core cues. No VfxDeltaTrackerMixin exists.

## Resonant Momentum

Resonant Momentum owns no custom HUD and no payload. It is the registered beneficial effect `jujutsumod:resonant_momentum` (`JujutsuEffects.RESONANT_MOMENTUM`), displayed by Minecraft's native effect UI; the explicit Nobara multipliers read effect presence server-side through `ResonantMomentum`. Status: VERIFIED.

Constants: `RESONANT_MOMENTUM_DURATION_TICKS = 1200`, `RESONANT_MOMENTUM_MULTIPLIER = 1.15f`, applied as `1.0 + (multiplier - 1.0) * level`, so a stacked amplifier scales the bonus rather than replacing it (VERIFIED — ProjectJjkNobaraProfile, ResonantMomentum).

It boosts hammer / directed-Hairpin (R) / Mega Nail (B) damage and speeds nail preparation and launch cadence. For reference, the base per-nail damage it multiplies is `HAIRPIN_DIRECTED_DAMAGE_PER_NAIL = 5.0f` for R (VERIFIED — `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java` — HAIRPIN_DIRECTED_DAMAGE_PER_NAIL) and `MEGA_NAIL_DAMAGE_PER_NAIL = 4.0f` (aliased to `HAIRPIN_ENLARGE_DAMAGE_PER_NAIL`) capped at `MEGA_NAIL_DAMAGE_CAP = 42.0f` for B (VERIFIED — `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java` — MEGA_NAIL_DAMAGE_PER_NAIL, MEGA_NAIL_DAMAGE_CAP).

## Mega Nail (B)

B (`ProjectJjkMegaNailRuntime.start`) merges every owned embedded nail on the aimed living target into one piercing strike. It collects the caster's embedded nails on the target, snapshots depth-weighted weight (sum of `nailDepthMultiplier`: 1.0 / 1.35 / 1.75 by embed depth) plus nail count, spawns a single mega-nail entity at the gather point in front of the caster, then atomically discards each consumed nail (one ENLARGE cue per nail) and consumes the target's marks (`ProjectJjkNailMarks.consume` + clear glowing mark). Returns UNHANDLED_FAILURE with no consumption when there is no living entity target or no embedded nails. Damage at impact is `min(MEGA_NAIL_DAMAGE_PER_NAIL * depthWeight, MEGA_NAIL_DAMAGE_CAP)` with ResonantMomentum multiplied separately; block hits and timeouts produce terminal VFX only, no damage (VERIFIED — `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkMegaNailRuntime.java` — start, megaNailDamage, onMegaNailImpact; `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraProfile.java` — MEGA_NAIL_DAMAGE_PER_NAIL, MEGA_NAIL_DAMAGE_CAP, nailDepthMultiplier).

This note is the owner of Resonance behaviour. [Target marks and Resonance](Target-marks-and-resonance.md) owns the mark half only.
