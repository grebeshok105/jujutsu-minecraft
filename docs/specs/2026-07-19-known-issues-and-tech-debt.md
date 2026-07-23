# Known Issues and Technical Debt

Status: CURRENT LIVE REGISTER

Last code verification: 2026-07-23

Applies to: main plus fix/persistence-nail-lifecycle-docs-sync

Owner hierarchy: current code/tests → AGENTS.md → SESSION.md → Codebase Codex → this register

## Accepted product decisions

### Global Resonance hit-stop

Resonance intentionally changes the global server tick rate to create hit-stop. This affects every player and dimension, but the current product target is private play for one or two people. Do not remove it as a generic multiplayer optimization. Reopen only if the target becomes a public or competitive server.

### ProjectJJK placeholder assets

ProjectJJK-named models/assets are temporary placeholders used with permission from the author. They may stay for private development and are intended to be replaced later. They are not automatically CC0. Before public distribution, preserve evidence and scope of permission or replace the remaining assets.

## Public-release blockers

### R1 — Rich-Modern provenance is unresolved

The client/rich package and associated font/shader assets were derived from a user-provided Rich-Modern reference. Dated research explicitly said study-only, while current source describes a port. Determine the upstream license/permission and replace code/assets that cannot be redistributed.

### R2 — Bundled Segoe UI font

src/main/resources/assets/jujutsumod/font/neon.ttf identifies as Segoe UI Semilight. The old Open Sans note was incorrect. The font is currently packaged even though ClickGui primarily uses MSDF atlases. Remove it if unused or replace it with an OFL font before public distribution.

### R3 — Placeholder release permission must be recorded

Private author permission is sufficient for current development. A public release still needs a recorded scope covering redistribution, or replacement with original assets.

## High-priority engineering work

### E1 — No automated in-game smoke test

CI compiles and runs assertion programs but does not boot a client or dedicated server. Renderer, mixin, packet, UI, and gameplay integration regressions can survive a green build.

Action: add GameTests/dedicated-server coverage first; keep real runClient smoke for graphics-dependent behavior.

### E2 — Curse-link options payload is not bounded

CurseLinkOptionsPayload trusts an incoming list size and unbounded technique string, while the client creates one button per entry.

Action: cap entries and string length, reject malformed ids, and add scrolling/pagination if the list can grow.

### E3 — Some server runtime state is still static and unevenly cleaned

CombatStagger, preparation state, anchor-removal tracking, and related maps use different cleanup rules. Most are server-thread safe, but long-running worlds need explicit ownership and pruning.

Action: centralize per-server state or add lifecycle/TTL cleanup with tests.

## Medium-priority work

### E4 — VFX delivery is transient and radius-filtered

Clients outside the broadcast radius at cast time do not receive a cue. This is acceptable for most short effects, but critical long-lived visuals need explicit state or catch-up rather than wider blind broadcast.

### E5 — Russian localization is incomplete

ru_ru.json has fewer keys than en_us.json. Synchronize all player-visible keys and add an automated key-set check.

### E6 — ClickGui rendering has avoidable per-shape work

Render2D immediately begins and flushes SDF for each shape to preserve MSDF ordering. SdfRenderer allocates/uploads per flush. Profile in-game before redesigning; if material, batch by render layer and reuse staging buffers.

### E7 — Second-character integration is still Nobara-shaped

Selection, UI cards, action payloads, loadout dispatch, and VFX registration contain direct Nobara branches. Do not build a giant abstraction early, but extract CharacterDefinition/handler boundaries when the second real kit is approved.

### E8 — Standard test reporting is weak

Nineteen custom JavaExec programs use main methods and assertions. They are useful and green, but do not provide normal per-test JUnit reports or GameTest world integration.

### E9 — Build reproducibility can improve

loom_version uses 1.17-SNAPSHOT, and CI currently tests one JDK. Pin a stable Loom release when available, add dependency locking if releases become important, and test Java 21 explicitly.

## Low-priority product debt

- Crafting recipes and broader datapack content are intentionally absent.
- Publication automation for Modrinth/CurseForge should wait until release provenance is clean.
- Some generic Rich ClickGui modules/components are unused and can be removed after confirming the final UI scope.
- The debug and research archive is large; keep it clearly historical rather than deleting decision evidence.

## Resolved on fix/persistence-nail-lifecycle-docs-sync

- Character selection now persists through Fabric Data Attachment API and is copied on death.
- Nobara starter tools are claimed once per player instead of being refilled on every selection.
- Loaded ordinary embedded nails expire after 1200 ticks and are capped at 30 per owner.
- Hairpin R/B resolve nails through EmbeddedNailRegistry instead of level.getAllEntities().
- README, AGENTS.md, SESSION.md, build instructions, Codex, and archive status are synchronized.
- Documentation audit tooling detects stale current-doc terms, missing historical markers, broken local links, and stale code-derived metrics.

## Historical findings no longer current

The 2026-07-19 full audit remains in docs/reviews as a historical report. Its VfxDeltaTrackerMixin, NailTrap return-value, old worktree, old GUI, old VFX-count, and other point-in-time findings must not be copied into the live backlog without re-verifying current code.
