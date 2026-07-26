# Session Handoff — Jujutsu Minecraft

## Current branch

- Branch: refactor/shared-vessel-render-stack
- Base: main at 21ebefe (merge of PR #4)
- PR #4 merged only the first Todo slice (c8e48dd) plus its review note (b3b4605)
- This branch rebases the remaining eight commits onto that merge: the GeckoLib model pass and review fixes (85792d8) and the shared vessel render stack (5be077b, 17d4a65, 69b9358, f4ccdea, 350aa86)
- Product target: private play for one or two people

Durable product state lives in AGENTS.md under "Current slice (facts)". This file records only what changed on this branch. Documentation authority order is owned by AGENTS.md; asset and provenance policy is owned by docs/PROVENANCE.md and docs/THIRD_PARTY_NOTICES.md.

## What changed on this branch

- Vessel rendering is now shared. CharacterGeoRenderers resolves one renderer per vessel through an exhaustive switch, so a new JujutsuCharacter constant fails compilation until it declares a renderer or opts into vanilla. CharacterPlayerGeoRenderer owns the render entry and pose-stack guard, CharacterPlayerGeoModel owns the arm pose and clamped head look, CharacterHeldItemLayer owns hand attachments.
- The three shared render mixins were renamed for their real scope: CharacterRenderDispatchMixin, PlayerRenderContextMixin, FirstPersonHandFxMixin. No behavior change.
- Todo gained a GeckoLib model, animations, and a player renderer (see below).

## Review-fix pass

Landed:

- A1 docs metrics / Todo source-of-truth updates
- A2 `JujutsuVfxRecipes.registerAll()`
- A5 TodoProfile horizontal radius + world-border margin wired into `TodoBoogieWoogieRuntime`
- A6 roster labels localized
- A7 trailing whitespace removed from the design doc
- B1 Black Flash bonus clears invulnerableTime for the bonus hit only
- B2 rollback logs incomplete restore

Not landed, and deliberately so:

- A3 entity-occupancy collision in safe destinations was **not** implemented. An earlier revision of this file claimed it as landed; that claim was false and has been removed. `TodoBoogieWoogieRuntime.findSafeDestination` still gates only on world bounds, chunk load, world border, and solid-block collision, and its own doc comment states "No floor, no third-party entity occupancy gates." The behavior is intentional for the current 1–2 player target. The remaining residue — an unused `otherSwapParticipant` parameter — is recorded as open debt in docs/KNOWN_ISSUES.md.

## Todo GeckoLib model pass

- Assets → `geckolib/models/todo/todo_aoi`, `geckolib/animations/todo/todo_aoi`, texture `textures/entity/character/todo_aoi.png`
- Client: `TodoPlayerGeoAnimatable` / `TodoPlayerGeoModel` / `TodoPlayerGeoRenderer`, plus the shared held-item layer
- Animations: idle, walk, attack, `ability.boogie_woogie` (triggered via VFX cue anchor on cast)

## Render debt pass

- CharacterGeoRenderer / CharacterGeoRenderers replace the per-character `if` chain; verified fail-closed by temporarily adding a fourth JujutsuCharacter constant and confirming the build fails at CharacterGeoRenderers
- CharacterHeldItemLayer, CharacterPlayerGeoRenderer, CharacterPlayerGeoModel absorb the duplicated vessel render stack
- Sanity-test guards repointed to the shared files and extended to assert no vessel redefines the head-look clamps or hand-rolls the pose-stack guard

## Verification status

- `gradlew.bat build --no-daemon` — BUILD SUCCESSFUL
- `python tools/audit_docs.py` — passing
- In-game client smoke — **NOT yet run on this branch.**

Compilation and the audit prove neither rendering nor gameplay feel. Run the client-smoke checklist in docs/BUILDING_IN_SANDBOX.md before treating this branch as verified.

## Next product steps

1. Run the client-smoke checklist in docs/BUILDING_IN_SANDBOX.md.
2. Add world/GameTest coverage for `TodoBoogieWoogieRuntime.tryCast` and its rollback path; nothing exercises them today (E1/E8 in docs/KNOWN_ISSUES.md).
3. Replace temporary ProjectJJK placeholders when original assets are available.
4. Resolve Rich-Modern provenance before any public distribution.
