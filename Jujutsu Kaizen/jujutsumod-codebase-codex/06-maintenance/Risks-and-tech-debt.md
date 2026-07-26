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
- Normalize cleanup of remaining static runtime state. Still open, and Todo added one: `TodoBoogieWoogieRuntime.PENDING_CLAP_SOUNDS` is a static queue drained only by END_WORLD_TICK, with no SERVER_STOPPING clear (VERIFIED).
- Complete ru_ru keys. Still open: 54 ru_ru keys against 88 en_us keys (VERIFIED — assets/jujutsumod/lang).
- Profile ClickGui SDF/MSDF batching before optimizing. Still open; note that `Render2D` deliberately flushes SDF per rect, so any profiling must treat that as a correctness constraint, not overhead to remove — see [GUI render pipelines](../04-client-vfx/GUI-render-pipelines.md).
- Legacy GeckoLib-4 `geo/` assets are dead weight and a naming trap — see [Assets and resources](../02-architecture/Assets-and-resources.md). Deliberately not deleted.

## Resolved

- Persistent selection, one-time starter claims, 1200-tick/30-owner embedded nail bounds, owner-indexed Hairpin lookup, and current-document consolidation.
- Extract second-character seams only after a real second kit — done. Todo is the real second kit, and the seams were extracted from it rather than ahead of it: `CharacterGeoRenderer`, `CharacterGeoRenderers.create`, `CharacterPlayerGeoRenderer`, `CharacterPlayerGeoModel`, `CharacterHeldItemLayer`, and the shared `CharacterAbility`/`CharacterAbilityExecutor` slot (VERIFIED). See [Vessel render stack](../04-client-vfx/Vessel-render-stack.md).
