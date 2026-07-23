# Nobara Explicit Hairpin Actions

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-08

## Problem

Hairpin Enlarge and Hairpin Explosion existed as server methods, but they were hidden behind hammer fallback behavior. This made the feature hard to find and easy to overclaim: the code path existed, but the player did not have clear ability inputs or UI visibility.

## Current Design

- Hammer right click launches prepared nails only.
- Shift + hammer remains Resonance.
- Hairpin Enlarge is a separate Nobara action payload, default key `R`.
- Hairpin Explosion / Boom is a separate Nobara action payload, default key `B`.
- Both keybinds and debug commands go through `ProjectJjkNobaraActions.tryCast`, so the server accepts those actions only when the player has selected Nobara.
- The V character screen shows Nobara's kit icons: Piercing, Enlarge (`R`), Boom (`B`), Resonance.

## ProjectJJK Parity

- Hairpin Enlarge range: `20`.
- Hairpin Enlarge damage: `12`.
- Hairpin Explosion damage: fixed `1`.
- Hairpin Explosion radius: `1.5`.

The VFX are a Fabric-native facsimile of ProjectJJK flash-strike / Flash32 behavior. We did not add GeckoLib or Specter as runtime dependencies for this slice.

## Verification Hooks

- `ProjectSanityTest.assertExplicitNobaraActionsAreVisible`
- `ProjectSanityTest.assertProjectJjkHairpinFinisherNumbers`
- `ProjectJjkNobaraProfileTest.assertDamageScaling`

