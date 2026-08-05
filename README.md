# Jujutsu Minecraft

A Fabric 1.21.8 combat mod built around a small number of deeply designed Jujutsu-inspired character kits. The current playable vessels are Nobara (nails, hammer, Hairpin, Resonance, traps, Black Flash), Todo (Boogie Woogie swap, heavy melee, shared Black Flash bridge), and Megumi (two independently mortal Divine Dogs), plus a shared cinematic VFX pipeline and character menu.

**New here, or on a new machine? Read [docs/START_HERE.md](docs/START_HERE.md) first** — clone, one command to prove the checkout, and what to read in which order.

## Current product

- Playable vessels: Nobara, Todo, Megumi, and None.
- Menu: press N to open ClickGui, choose a vessel, then confirm it.
- Nobara actions: R for directed Hairpin, B for the Mega Nail (merges every nail embedded in the aimed target into one piercing strike), Shift+R for Self Resonance, Shift+B for Nail Trap, and left click with the hammer for contextual melee. Targets carrying your nails show a personal ESP badge only you can see.
- Todo actions: R for Boogie Woogie (server-authoritative self↔target swap; nothing under the crosshair is a plain refusal), Shift+R for a feint clap that looks and sounds identical but moves nobody, B twice to swap two bystanders with each other, Shift+B after a B mark for the triple cycle (you take the marked body's place, it takes the aimed body's place, the aimed body takes yours), V to throw the stone or trade places with it, Shift+V to trade the aimed body with the stone; vanilla melee with Todo modifiers and Black Flash bridge.
- Megumi actions: R raises both Divine Dogs from shadow or recalls the surviving pack; Shift+R commands every living dog to attack the aimed eligible target and enables its independent mid-range pounce. Summoning has no cooldown, recall has a 12-second cooldown, and losing the final dog has a 30-second cooldown; the remaining time appears on Megumi's combat HUD.
- Todo's stone is a slow, clearly visible projectile that lives about five seconds and exists only in flight: it hurts nobody, marks nothing, and disappears on any collision — it never becomes a lasting anchor. While it flies it is a swap point you can take yourself (V) or feed an enemy into (Shift+V); a swap trades positions only, so the stone keeps sailing on from wherever its partner stood.
- A completed swap is a short physical beat: a camera snap on the clap, a compact burst and a brief afterimage where each body stood, an inward gather where each one lands, the world's own noise stepping back for a fraction of a second, and a low report when it settles. A feint gets the clap and nothing else. Landing a swap also opens a short window — the next hit that connects lands harder and staggers, and a miss does not spend it.
- Gameplay authority is server-side; rendering, menus, particles, camera work, and client animation stay under src/client.
- Character selection persists through reconnects and restarts. Re-selecting Nobara restores any missing starter tools without duplicating ones still held; Todo and Megumi have no starter items in this slice.
- The current target is private play for one or two people, not a public competitive server. Several accepted tradeoffs follow from that.

Exact tuning values, contracts, and accepted tradeoffs live in [AGENTS.md](AGENTS.md) under "Current slice (facts)"; unresolved debt lives in [docs/KNOWN_ISSUES.md](docs/KNOWN_ISSUES.md).

## Adding a vessel

A new vessel is one enum constant, one server definition (`CharacterDefinition`, bound in `JujutsuCharacters`), one client definition (`CharacterClientDefinition`, bound in `JujutsuCharacterClients`), and assets — no shared file changes, because each definition installs its own hooks at init. The two registries' exhaustive switches fail the build until both halves exist. Rendering goes through the shared vessel stack (`CharacterPlayerGeoRenderer`, `CharacterPlayerGeoModel`, `CharacterHeldItemLayer` under src/client) rather than a copied one. The full procedure lives in the Codex note [How to add the next character](Jujutsu%20Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md); the contract itself in [Vessel definitions](Jujutsu%20Kaizen/jujutsumod-codebase-codex/02-architecture/Vessel-definitions.md).

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
