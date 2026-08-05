# jujutsumod Codebase Codex — Map of Content

Status: CURRENT

Verified: 2026-08-03

Code target: current checkout of main or the active feature branch

## Authority

1. Current code and passing tests.
2. Root AGENTS.md for durable rules.
3. Root SESSION.md for the active handoff.
4. This Codex for architecture/navigation.
5. docs/KNOWN_ISSUES.md for live debt.

The repository intentionally keeps no documentation archive. Prefer repo-relative path + symbol; add a commit SHA when a statement must remain point-in-time.

## Current product snapshot

- Fabric 1.21.8, Java 21, mod id jujutsumod.
- Playable vessels: Nobara, Todo (Aoi Todo), Megumi, and None.
- N opens ClickGui; Characters is live and Soon rows are inert.
- Selection is server-authoritative and persistent through Fabric Data Attachment API.
- Nobara's starter kit is restored idempotently on every selection; Todo and Megumi have no starter items in their current slices.
- Shared input slots — R, Shift+R, B, Shift+B, and left click with a technique weapon — mean whatever the selected vessel's own router says; each vessel binds one server and one client definition. See [Vessel definitions](02-architecture/Vessel-definitions.md).
- Nobara, Todo and Megumi use ordinary 64x64 player skins through vanilla `PlayerRenderer`; one shared GeckoLib-to-`PlayerModel` bridge evaluates their authored third-person packs on invisible rigs. Nobara uses `slim`, Todo and Megumi use `wide`, and Todo's body scale is 1.15 without reach/damage/speed changes. NONE keeps the ordinary vanilla pose. The former visible Geo player stack and superseded skin packs are retained outside runtime under `archive/character-player-gecko/` and `archive/character-skin-animation/`.
- Transient combat effects use VfxCue → VfxDirector → character recipes, each registered by its vessel's client definition. Megumi owns `jujutsu.mod.vfx.MegumiVfxIds` and `jujutsu.mod.client.vfx.megumi.MegumiVfxRecipes`; his twelve `megumi/*` wire ids are five Divine Dogs plus seven shadow-kit ids, appended and never renumbered.
- World transient rendering keeps lifecycle and dispatch in `VfxWorldChannel` while `HairpinWorldEffects`, `BlackFlashWorldEffects`, `SwapWorldEffects`, `ShadowWorldEffects`, and shared `VfxWorldGeometry` own the extracted visual families; the cap remains 48.
- `VfxCameraChannel` keeps production wall-clock behavior through `System::currentTimeMillis` while its package-private millisecond supplier seam makes deterministic start, expiry, overlap, clamp, strength, and clear contracts executable without sleeps.
- A completed Boogie Woogie emits its own cues — afterimage and arrival — which the feint does not, and opens a 24-tick window on Todo's next hit. The marker system is deleted: Todo's stone exists only in flight (V throws or self-swaps, Shift+V feeds a target into it), and B → Shift+B runs the fixed triple cycle Todo→A→T→Todo. Contracts: [Todo — Boogie Woogie and combat slice](03-systems/Todo-Boogie-Woogie.md), design `docs/TODO_STONE_REWORK.md`.
- Loaded ordinary embedded nails use a 1200-tick TTL, a 30-per-owner cap, and EmbeddedNailRegistry.
- Resonance global server hit-stop is intentional for the private 1–2 player target.

## Code-derived metrics

| Metric | Value |
|---|---:|
| Main Java files | 124 |
| Client Java files | 185 |
| Test Java files | 80 |
| Verification programs | 29 |
| Client mixins | 6 |
| Network payloads | 8 |
| Nobara VFX ids | 24 |

Verification programs counts JavaExec main() programs only. Since the JUnit foundation landed, a test class may instead be a JUnit class run by the standard test task; those are counted under Test Java files and not by that row. Both kinds run inside ./gradlew qualityGate.

The audit runs inside ./gradlew qualityGate, so these counters are checked before a commit rather than after a push.

## Meta

- [Version and identity](01-meta/Version-and-identity.md)
- [Sources and method](01-meta/Sources-and-method.md)
- [Citation standard](01-meta/Citation-standard.md)
- [Uncertainties](01-meta/Uncertainties.md)
- [Code graph status](01-meta/Codegraph-status.md)

## Architecture

- [Entrypoints and lifecycle](02-architecture/Entrypoints-and-lifecycle.md)
- [Registries](02-architecture/Registries.md)
- [Vessel definitions](02-architecture/Vessel-definitions.md)
- [Networking](02-architecture/Networking.md)
- [Client-server boundaries](02-architecture/Client-server-boundaries.md)
- [Assets and resources](02-architecture/Assets-and-resources.md)

## Gameplay systems

- [Character selection](03-systems/Character-selection.md)
- [Nobara overview](03-systems/Nobara-overview.md)
- [Nobara runtime flow](03-systems/Nobara-runtime-flow.md)
- [Nail entity lifecycle](03-systems/Nail-entity-lifecycle.md)
- [Target marks](03-systems/Target-marks-and-resonance.md)
- [Straw Doll Resonance](03-systems/Straw-Doll-resonance.md)
- [Combat expansion](03-systems/Nobara-combat-expansion.md)
- [Combat timing and Black Flash](03-systems/Combat-timing-and-black-flash.md)
- [Curse links](03-systems/Curse-links.md)
- [Todo Boogie Woogie](03-systems/Todo-Boogie-Woogie.md)
- [Megumi Divine Dogs](03-systems/Megumi-Divine-Dogs.md)
- [Megumi shadow kit](03-systems/Megumi-shadow-kit.md)

## Client and VFX

- [VFX Core](04-client-vfx/VFX-core.md)
- [Vessel render stack](04-client-vfx/Vessel-render-stack.md)
- [Nail rendering](04-client-vfx/Nail-rendering.md)
- [Character select GUI](04-client-vfx/GUI-character-select.md)
- [GUI render pipelines](04-client-vfx/GUI-render-pipelines.md)

## Reference and maintenance

- [Claim-to-source index](05-reference/Claim-Source-Index.md)
- [Public API surface](05-reference/Public-api-surface.md)
- [Test and build commands](05-reference/Test-and-build-commands.md)
- [ProjectJJK parity map](05-reference/ProjectJJK-parity-map.md)
- [One-to-one checklist](05-reference/One-to-one-checklist.md)
- [How to add the next character](06-maintenance/How-to-add-next-character.md)
- [Risks and technical debt](06-maintenance/Risks-and-tech-debt.md)
