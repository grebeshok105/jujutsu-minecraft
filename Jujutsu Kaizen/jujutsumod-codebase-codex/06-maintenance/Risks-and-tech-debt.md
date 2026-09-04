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

- Add GameTest/dedicated-server and real runClient smoke coverage. Still open: the `gametest` source set exists with neutral server canaries (`src/gametest/java/jujutsu/mod/gametest/ServerGameTests.java — ServerGameTests`, `runGameTest` gated in `build.gradle`) plus a non-required client lane (`runClientGameTest`, client canaries) — but no automated check casts an ability or exercises world behaviour (VERIFIED — docs/KNOWN_ISSUES.md E1). See E1 in docs/KNOWN_ISSUES.md.
- Bound CurseLinkOptionsPayload. Codec bounds are implemented — `MAX_ENTRIES = 64` rejects over-cap counts before allocation and `MAX_TECHNIQUE_ID_LENGTH = 256` bounds technique-id strings, with the writer validating before write (VERIFIED — src/main/java/jujutsu/mod/network/CurseLinkOptionsPayload.java — read/write). Still open: no canonical catalog of supported technique ids, so a well-formed unknown id cannot be rejected (VERIFIED — docs/KNOWN_ISSUES.md E2). See E2 in docs/KNOWN_ISSUES.md.
- Normalize cleanup of remaining static runtime state. Still open in general; Todo's own pending-sound queue is now cleared on SERVER_STOPPING (VERIFIED).
- Enforce localization parity automatically. The key gap itself is closed — en_us and ru_ru both hold 140 keys with an empty difference in both directions (recounted 2026-09; rerun before trusting) — but nothing in `check` compares the two key sets (VERIFIED — only per-key `contains` asserts exist, e.g. src/test/java/jujutsu/mod/ProjectSanityTest.java — Straw Doll tooltip, MegumiCooldownHudTest), so the next English key will silently drift again. See E5 in docs/KNOWN_ISSUES.md.
- Profile ClickGui SDF/MSDF batching before optimizing. Still open; note that `Render2D` deliberately flushes SDF per rect, so any profiling must treat that as a correctness constraint, not overhead to remove — see [GUI render pipelines](../04-client-vfx/GUI-render-pipelines.md).
- Legacy unrelated GeckoLib-4 `geo/` assets remain governed by their own provenance; replaced player Geo files are retained outside runtime in `archive/character-player-gecko/` — see [Assets and resources](../02-architecture/Assets-and-resources.md).

## Resolved

- Persistent selection, the idempotent Nobara starter-kit restore on every selection, 1200-tick/30-owner embedded nail bounds, owner-indexed Hairpin lookup, and current-document consolidation.
- Extract second-character seams only after a real second kit — done. Todo is the real second kit, and the shared GeckoLib-to-vanilla skin animation bridge, shared `CharacterAbility`/`CharacterAbilityExecutor` slot, and per-vessel definitions (`CharacterDefinition`/`JujutsuCharacters`, `CharacterClientDefinition`/`JujutsuCharacterClients`) now serve the roster. The former visible player Geo stack remains in the archive. See [Vessel definitions](../02-architecture/Vessel-definitions.md) and [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
