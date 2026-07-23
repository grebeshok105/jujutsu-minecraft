# Session Handoff — Nobara Straw Doll, VFX, and Held Items

> **Status: HISTORICAL REFERENCE.** This dated research/design/review record is not the current source of truth. For current behavior use `README.md`, `AGENTS.md`, `SESSION.md`, and `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md`; current code and tests win on conflict.

Date: 2026-07-11

## Current State

- Worktree: `D:\WorkFlow\Jujutsu Minecraft\.worktrees\nobara-cinematic-slice`
- Branch: `codex/nobara-cinematic-slice`
- HEAD: `33ef9c2 fix(nobara): render held items on geo model`
- The main checkout is user-owned and dirty. Do not edit, reset, clean, or merge it without explicit user direction.
- This file is canonical. `2026-07-10-vfx-core-implementation-handoff.md` is historical context only; its JAR hash and HEAD are stale.

## Completed Scope

Requirements: `docs/research/2026-07-10-nobara-straw-doll-canon.md`, `docs/superpowers/specs/2026-07-10-nobara-straw-doll-vfx-expansion-design.md`, and `docs/superpowers/plans/2026-07-10-nobara-straw-doll-vfx-expansion.md`.

Implemented range: `174aea6 feat(nobara): add resonance remnant contract` through `33ef9c2`. It adds server-authoritative remnant progression and ritual, the original animated Straw Doll, VFX through `VfxCue -> VfxDirector -> NobaraVfxRecipes`, decoupled Hairpin marks, localized remnant names, safe runtime packaging, and held-item rendering on the Nobara model.

## Current Gameplay Contract

Resonance is not activated by using the doll itself.

1. Land two accepted ordinary, non-explosive nail hits on the same target and pick up the bound remnant.
2. Hold the Straw Doll in offhand, the Straw Doll Hammer in main hand, and keep one nail in inventory.
3. Sneak and right-click with the hammer.
4. At successful impact, exactly one matching remnant and one nail are consumed. Doll and hammer remain.

The target must be alive, loaded, in the same dimension, and within 64 blocks. Line of sight is not required. Resolution applies `20.0f` magic damage and Weakness III for 80 ticks. Hairpin Enlarge (`R`) and Boom (`B`) are mark-based; Resonance does not consume marks.

Relevant files: `ProjectJjkStrawDollRuntime.java`, `ProjectJjkResonanceRemnant.java`, `ProjectJjkNobaraRuntime.java`, and `ProjectJjkNobaraProfile.java` under `src/main/java/jujutsu/mod/character/nobara/projectjjk/`.

## Latest Held-Item Fix

Root cause: `NobaraLivingEntityRendererMixin` cancels vanilla player rendering for selected Nobara, so vanilla's held-item layer never runs.

`33ef9c2` keeps the GeckoLib player model and adds:

- `NobaraHeldItemLayer` for real main-hand/offhand items on `rightHandItem` and `leftHandItem` bones.
- Two attachment bones parented to the matching elbow/wrist chain in `nobara_kugisaki.geo.json`.
- A vanilla `PlayerModel` pose bridge for held/use poses; custom ProjectJJK action animation retains priority.
- Straw Doll guidance through current `ItemLore`, not the deprecated tooltip callback.
- `ProjectSanityTest` coverage for the layer, pose bridge, attachment bones, and translations.

Key files: `src/client/java/jujutsu/mod/client/render/nobara/NobaraHeldItemLayer.java`, `NobaraPlayerGeoRenderer.java`, `NobaraPlayerGeoModel.java`, `src/main/resources/assets/jujutsumod/geckolib/models/projectjjk/nobara_kugisaki.geo.json`, and `src/main/java/jujutsu/mod/registry/JujutsuItems.java`.

## Verification And Installed Artifact

Fresh commands after the held-item fix: `gradlew.bat testProjectSanity compileClientJava --no-daemon`, `gradlew.bat check --no-daemon`, `gradlew.bat build --no-daemon -x test`, and `git diff --check`.

All commands completed with `BUILD SUCCESSFUL`; all nine Java assertion tasks passed. The final JAR was checked for `NobaraHeldItemLayer` and required Straw Doll assets; it has no `source-assets/**` entries and no ProjectJJK doll copy.

- Built JAR: `build\libs\jujutsumod-1.0.0.jar`
- Installed JAR: `D:\Games\instances\Jujutsu\mods\jujutsumod-1.0.0.jar`
- Size: `2,139,290` bytes
- SHA-256: `8FE567E567B6D60D2BE3D32CCB8CBD70F2C38FF5F11D8B98175095FC92A5747B`
- Source and installed JAR hashes and sizes matched.
- Review archive: `D:\WorkFlow\Jujutsu Minecraft\nobara-cinematic-slice-review-33ef9c2.zip`

## Review Status And Manual QA

Previous independent technical/spec reviews cleared the Straw Doll/VFX code after blockers were fixed. The later held-item fix has focused RED/GREEN coverage plus full `check` and `build`, but it has not received a new external review.

Do not claim visual feel from compilation. The user performs manual QA; Computer Use/UI automation was not used.

- Verify hammer/doll rendering, swap hands, and check attachment/scale.
- Hold/use a normal item, shield, bow/crossbow, and Straw Doll Hammer; custom attack/snap animations must retain item attachment.
- Verify Russian Straw Doll tooltip, full ritual, target invalidation, remote hit through obstacles, R/B Hairpin behavior, reduced particles, and two-client observer behavior.

## Resume Protocol

1. Work only in this linked worktree and run `git status --short --branch` before edits.
2. Read `AGENTS.md`, this handoff, the plan, and `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md` before gameplay/VFX work.
3. For ProjectJJK/Nobara work, consult `mcpvault` read-only first. The live vault is rooted in main, so edit versioned notes only inside this worktree.
4. Use codebase-memory project `D-WorkFlow-Jujutsu-Minecraft-.worktrees-nobara-cinematic-slice` for code discovery, then source reads when its index is stale.
5. For new behavior, follow the design/plan/TDD gate. Verify, then copy only the non-sources/non-dev JAR into the game instance.

## Suggested Skills

- `using-git-worktrees`
- `brainstorming` before new gameplay/VFX/model behavior
- `test-driven-development`
- `systematic-debugging` for failed checks or rendering defects
- `blockbench-use` before any Blockbench MCP modification
- `verification-before-completion`
- `handoff` before another session boundary
