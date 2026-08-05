# Risks and Technical Debt

Status: CURRENT SUMMARY

The live detailed register is docs/KNOWN_ISSUES.md.

## Accepted

- Global Resonance server hit-stop for the private 1–2 player target.
- Temporary ProjectJJK placeholders used with author permission and replaced later.

## Public-release blockers

- Rich-Modern provenance/license is unresolved.
- neon.ttf is Segoe UI Semilight.
- Public redistribution scope for placeholders must be recorded or assets replaced.

## Engineering priorities

- Add GameTest/dedicated-server and real runClient smoke coverage. Still open: no `src/gametest` source set exists (VERIFIED).
- Bound CurseLinkOptionsPayload. Still open: `read` sizes its list from a raw `readVarInt` with no cap (VERIFIED — CurseLinkOptionsPayload.read).
- Normalize cleanup of remaining static runtime state. Still open in general; Todo's own pending-sound queue is now cleared on SERVER_STOPPING (VERIFIED).
- Enforce localization parity automatically. The key gap itself is closed — en_us and ru_ru both hold 95 keys with an empty difference (VERIFIED — assets/jujutsumod/lang) — but nothing in `check` compares the two sets, so the next English key will silently drift again. See E5 in docs/KNOWN_ISSUES.md.
- Profile ClickGui SDF/MSDF batching before optimizing. Still open; note that `Render2D` deliberately flushes SDF per rect, so any profiling must treat that as a correctness constraint, not overhead to remove — see [GUI render pipelines](../04-client-vfx/GUI-render-pipelines.md).
- Legacy unrelated GeckoLib-4 `geo/` assets remain governed by their own provenance; replaced player Geo files are retained outside runtime in `archive/character-player-gecko/` — see [Assets and resources](../02-architecture/Assets-and-resources.md).

## Resolved

- Persistent selection, the idempotent Nobara starter-kit restore on every selection, 1200-tick/30-owner embedded nail bounds, owner-indexed Hairpin lookup, and current-document consolidation.
- Extract second-character seams only after a real second kit — done. Todo is the real second kit, and the shared GeckoLib-to-vanilla skin animation bridge, shared `CharacterAbility`/`CharacterAbilityExecutor` slot, and per-vessel definitions (`CharacterDefinition`/`JujutsuCharacters`, `CharacterClientDefinition`/`JujutsuCharacterClients`) now serve the roster. The former visible player Geo stack remains in the archive. See [Vessel definitions](../02-architecture/Vessel-definitions.md) and [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
