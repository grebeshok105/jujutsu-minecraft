# Nobara Hairpin Gameplay Slice

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Status: accepted for implementation.

Sources:

- `docs/research/2026-07-07-nobara-gameplay-research-synthesis.md`
- `docs/research/sources/2026-07-07-nobara-gameplay-design.md`
- `docs/research/sources/2026-07-07-minecraft-fabric-combat-system.md`
- `docs/research/sources/2026-07-07-fabric-ability-system-architecture.md`
- Existing Hairpin VFX and networking slice.

## Goal

Build the first playable Nobara Hairpin gameplay loop: prepare nails, strike with the hammer, launch cursed-energy nails at a server-selected target, apply server-authoritative damage and knockback, then play the existing blood-black Hairpin cinematic.

## Locked Decisions

- Flow: two-step combo.
- Control: item-first gameplay.
- Targeting: reusable server-side resolver; v1 uses look ray plus soft entity sweep.
- Blue aura: flight trail only.
- Balance: sandbox power fantasy.
- Scope: Hairpin only. Resonance, Straw Doll proxy, Black Flash, Domain Expansion, Infinity, custom soul damage, cursed energy, and player animation dependency are deferred.

## Gameplay

1. Right-click with `jujutsumod:hairpin_nail` to prepare a volley.
2. The server consumes up to four nails from the inventory in survival and stores them as prepared nails.
3. Prepared nails expire after 100 ticks if not used.
4. Right-click with `jujutsumod:straw_doll_hammer` while a volley is prepared.
5. Server validates cooldown and cast phase, then starts an 8 tick windup.
6. On launch, server resolves a target up to 32 blocks away.
7. Four nail flight visuals are broadcast with blue cursed-energy trails.
8. After 10 ticks of flight, server applies damage and knockback at the resolved target point.
9. Existing `HairpinFxPayload` plays the final blood-black cinematic impact.
10. Hammer enters 14 tick recovery and 70 tick cooldown.

## Server Rules

- Server is the only authority for prepared nail count, phase, cooldown, target, damage, and knockback.
- Client visuals are cosmetic.
- Owner is immune in this first sandbox slice.
- Damage radius is 4.25 blocks.
- Damage is `min(64, 32 + 8 * nailCount)`.
- Knockback strength is `1.4 + 0.25 * nailCount`.
- Damage source v1 uses the owner's player attack source for compile-safe Hairpin damage.
- State is non-persistent and clears on death, logout, or dimension change.

## Interfaces

- Items:
  - `jujutsumod:hairpin_nail`
  - `jujutsumod:straw_doll_hammer`
- New S2C payload:
  - `jujutsumod:hairpin_nail_flight`
  - `jujutsumod:prepared_nails`
- Debug commands:
  - `/jujutsu debug nobara_state`
  - `/jujutsu debug nobara_clear`
- Existing preview command remains:
  - `/jujutsu hairpin`

## Verification

- Add JavaExec tests for targeting, combat state, and Hairpin gameplay math.
- Existing visual and project sanity tests must still pass.
- Final command:
  - `.\gradlew.bat check build --no-daemon -x test`
- In-game smoke:
  - `/jujutsu give nobara_tools`
  - Prepare nails with right-click.
  - Launch with hammer.
  - Confirm blue nail flight, blood-black impact, damage/knockback, cooldown, and debug state.
