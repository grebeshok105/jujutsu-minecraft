# Nobara Hammer and Momentum Effect Implementation Plan

> **Status: COMPLETE 2026-07-12.** Native Momentum effect, restored embedded nail anchors, and compact Blockbench hammer shipped. Installed JAR SHA-256: `A93302AE6F7924C340D2D1DF6DCEC5523FFE8A555E11EF724A0E3367E1084116`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the custom Momentum HUD with a native beneficial effect and rebuild the hammer as a compact silver claw hammer.

**Architecture:** Keep all Momentum gameplay multipliers at their current explicit server call sites, but make a registered `MobEffect` the duration/source of truth. Rebuild only the hammer asset/model/display transforms; do not alter combat timelines or animations.

**Tech Stack:** Java 21, Fabric 1.21.8, Mojang mappings, Blockbench MCP, Java item model JSON.

## Global Constraints

- Work only in the Nobara isolated worktree.
- Use public APIs and server-authoritative effect state.
- Preserve item ids, animation bones, damage, and timing balance.
- Save/export a Blockbench source and verify the runtime JAR installation.

### Task 1: Native Momentum effect

**Files:** register the effect under `src/main/java/jujutsu/mod/registry`, modify `ResonantMomentum`, Straw Doll grant and explicit multiplier call sites; remove custom HUD/client state/payload; add effect icon and localization; update focused tests.

- [ ] Add failing guards requiring native effect registration and absence of `ResonantMomentumHud`.
- [ ] Register a beneficial no-attribute effect and apply/refresh it for exactly 1200 ticks.
- [ ] Read server activity from `hasEffect`, preserve multiplier semantics, and remove redundant synchronization/HUD code.
- [ ] Run focused tests and commit `refactor(nobara): show momentum as native effect`.

### Task 2: Compact silver hammer asset

**Files:** Blockbench source asset, hammer Java model JSON, hammer textures, asset guards.

- [ ] Preflight Blockbench MCP, checkpoint, and inspect the active project.
- [ ] Block out a compact one-handed handle, silver head, round face and split claw.
- [ ] Apply a restrained silver/dark-steel/wood palette and verify silhouette with screenshots.
- [ ] Export Java model JSON and source `.bbmodel`; tune GUI/first-/third-person/ground transforms.
- [ ] Add structural/resource checks and commit `feat(items): redesign Nobara hammer`.

### Task 3: Final verification and install

- [ ] Update `SESSION.md`, handoff, and Obsidian Nobara maintenance note.
- [ ] Run focused tests, `gradlew.bat check --no-daemon`, production build, and `git diff --check`.
- [ ] Replace the installed runtime JAR and compare SHA-256.
