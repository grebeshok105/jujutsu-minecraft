# jujutsumod Codebase Codex — Map of Content

Status: CURRENT

Verified: 2026-07-26

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
- Playable vessels: Nobara, Todo (Aoi Todo), and None.
- N opens ClickGui; Characters is live and Soon rows are inert.
- Selection is server-authoritative and persistent through Fabric Data Attachment API.
- Nobara's starter kit is restored idempotently on every selection; Todo has no starter items in the first slice.
- Shared input slots — R, Shift+R, B, Shift+B, and left click with a technique weapon — mean whatever the selected vessel's own router says; each vessel binds one server and one client definition. See [Vessel definitions](02-architecture/Vessel-definitions.md).
- Vessel bodies are GeckoLib replaced-player renders behind one shared stack; NONE keeps the vanilla model.
- Transient combat effects use VfxCue → VfxDirector → character recipes, each registered by its vessel's client definition.
- A completed Boogie Woogie emits its own cues — afterimage and arrival — which the feint does not, and opens a 24-tick window on Todo's next hit. A landed marker is a reusable anchor; a mark on a body still expires and is still consumed.
- Loaded ordinary embedded nails use a 1200-tick TTL, a 30-per-owner cap, and EmbeddedNailRegistry.
- Resonance global server hit-stop is intentional for the private 1–2 player target.

## Code-derived metrics

| Metric | Value |
|---|---:|
| Main Java files | 100 |
| Client Java files | 165 |
| Test Java files | 34 |
| Verification programs | 34 |
| Client mixins | 6 |
| Network payloads | 8 |
| Nobara VFX ids | 25 |

Run python3 tools/audit_docs.py after changing facts represented above.

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
