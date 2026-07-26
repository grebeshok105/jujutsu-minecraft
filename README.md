# Jujutsu Minecraft

A Fabric 1.21.8 combat mod built around a small number of deeply designed Jujutsu-inspired character kits. The current playable vessels are Nobara (nails, hammer, Hairpin, Resonance, traps, Black Flash) and Todo (Boogie Woogie swap, heavy melee, shared Black Flash bridge), plus a shared cinematic VFX pipeline and character menu.

## Current product

- Playable vessels: Nobara, Todo, and None.
- Menu: press N to open ClickGui, choose a vessel, then confirm it.
- Nobara actions: R for directed Hairpin, B for mass Hairpin, Shift+R for Self Resonance, Shift+B for Nail Trap, and left click with the hammer for contextual melee.
- Todo actions: R for Boogie Woogie (server-authoritative self↔target swap); vanilla melee with Todo modifiers and Black Flash bridge.
- Gameplay authority is server-side; rendering, menus, particles, camera work, and client animation stay under src/client.
- Character selection persists through reconnects and restarts. The Nobara starter kit is granted once per player; Todo has no starter items in this slice.
- The current target is private play for one or two people, not a public competitive server. Several accepted tradeoffs follow from that.

Exact tuning values, contracts, and accepted tradeoffs live in [AGENTS.md](AGENTS.md) under "Current slice (facts)"; unresolved debt lives in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).

## Adding a vessel

Start at the shared vessel render stack — `CharacterGeoRenderers` plus `CharacterPlayerGeoRenderer`, `CharacterPlayerGeoModel`, and `CharacterHeldItemLayer` under src/client. A new vessel supplies assets and hooks to those shared classes rather than copying a render stack; the exhaustive switch in `CharacterGeoRenderers` fails the build until a new character declares a renderer.

## Requirements

- Java 21
- Minecraft 1.21.8
- Fabric Loader 0.19.3 or newer
- Fabric API
- GeckoLib 5.2.2 or newer

## Build and verification

```bash
./gradlew build --no-daemon
```

The build compiles both environment source sets, runs the Gradle test task, and runs all custom assertion-based verification programs wired into check. A successful remapped jar is written to build/libs/jujutsumod-1.0.0.jar.

Compilation does not prove rendering or gameplay feel. UI, combat, mixins, and VFX changes still require an in-game client smoke test.

[docs/BUILDING_IN_SANDBOX.md](docs/BUILDING_IN_SANDBOX.md) owns the full verification recipe: the documentation audit, focused verification tasks, the client-smoke checklist, and the restricted-container workarounds.

## Documentation hierarchy

[AGENTS.md](AGENTS.md) owns the authoritative order under "Documentation Authority". Start there, then use [docs/README.md](docs/README.md) as the map of current operational docs.

## Asset status and license

The repository license is CC0-1.0 for original project code and materials where the project has the right to apply it. Not everything in the tree is covered by it: see [docs/PROVENANCE.md](docs/PROVENANCE.md) and [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md), which own the placeholder-asset and font notices and the open provenance questions. Read both before any public distribution.
