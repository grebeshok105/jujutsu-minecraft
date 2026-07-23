# Jujutsu Minecraft

A Fabric 1.21.8 combat mod built around a small number of deeply designed Jujutsu-inspired character kits. The current playable vertical slice is Nobara: nails, hammer combat, directed and mass Hairpin, Resonance, nail traps, Black Flash, a character menu, and a shared cinematic VFX pipeline.

## Current product

- Playable vessels: Nobara and None.
- Menu: press N to open ClickGui, choose a vessel, then confirm it.
- Nobara actions: R for directed Hairpin, B for mass Hairpin, Shift+R for Self Resonance, Shift+B for Nail Trap, and left click with the hammer for contextual melee.
- Gameplay authority is server-side; rendering, menus, particles, camera work, and client animation stay under src/client.
- Character selection persists through reconnects and restarts. The Nobara starter kit is granted once per player.
- Loaded ordinary embedded nails expire after 1200 ticks and are capped at 30 per owner.
- Resonance intentionally applies global server hit-stop. The current target is private play for one or two people, not a public competitive server.

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

For the restricted Hyperagent sandbox recipe, see [docs/BUILDING_IN_SANDBOX.md](docs/BUILDING_IN_SANDBOX.md).

Compilation does not prove rendering or gameplay feel. UI, combat, mixins, and VFX changes still require an in-game client smoke test.

## Documentation hierarchy

1. Current code and tests are authoritative for behavior.
2. [AGENTS.md](AGENTS.md) owns durable product and engineering rules.
3. [SESSION.md](SESSION.md) records the current branch, changes, and verification state.
4. [Codebase Codex MOC](Jujutsu%20Kaizen/jujutsumod-codebase-codex/00-MOC.md) indexes current architecture and maintenance notes.
5. [docs/README.md](docs/README.md) explains the archive. Dated research, plans, reviews, and handoffs are historical records, not current instructions.

## Asset status and license

The repository license is CC0-1.0 for original project code and materials where the project has the right to apply it. Some paths named projectjjk contain temporary placeholder/reference assets used with permission from the ProjectJJK author. They are not intended as the final public asset set and must not be treated as CC0 source material. Replace them or document release permission before a public distribution.

The Rich-Modern reference and port also require a separate provenance review before public release. See [docs/research/projectjjk/legal/README_IMPORT_NOTES.md](docs/research/projectjjk/legal/README_IMPORT_NOTES.md) and the current risk register in the Codex.
