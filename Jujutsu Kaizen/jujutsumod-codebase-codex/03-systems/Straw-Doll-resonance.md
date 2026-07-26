# Straw Doll Resonance

Status: CURRENT

Ordinary accepted nail hits advance target-bound remnant progress. The ritual requires a matching remnant, nail, hammer, doll, valid target, same dimension, range, and no duplicate pending cast. Final impact revalidates before consuming resources and applying 28 damage.

A successful impact grants Resonant Momentum, applies heavy CombatStagger, and triggers ServerTimeDilation at 10 TPS for 20 server ticks. This global server hit-stop is an explicit accepted decision for the current private 1–2 player target. It is not a client-only effect and must be reconsidered if the product becomes public multiplayer.

DOLL_STRIKE and RESONANCE_RELEASE remain transient VFX Core cues. No VfxDeltaTrackerMixin exists.

## Resonant Momentum

Resonant Momentum owns no custom HUD and no payload. It is the registered beneficial effect `jujutsumod:resonant_momentum` (`JujutsuEffects.RESONANT_MOMENTUM`), displayed by Minecraft's native effect UI; the explicit Nobara multipliers read effect presence server-side through `ResonantMomentum`. Status: VERIFIED.

Constants: `RESONANT_MOMENTUM_DURATION_TICKS = 1200`, `RESONANT_MOMENTUM_MULTIPLIER = 1.15f`, applied as `1.0 + (multiplier - 1.0) * level`, so a stacked amplifier scales the bonus rather than replacing it (VERIFIED — ProjectJjkNobaraProfile, ResonantMomentum).

It boosts hammer / directed-Hairpin (R) / Boom (B) damage and speeds nail preparation and launch cadence. For reference, the base per-nail damage it multiplies is `HAIRPIN_DIRECTED_DAMAGE_PER_NAIL = 5.0f` for R and `HAIRPIN_BOOM_DAMAGE_PER_NAIL = 3.0f` for B (VERIFIED).

This note is the owner of Resonance behaviour. [Target marks and Resonance](Target-marks-and-resonance.md) owns the mark half only.
