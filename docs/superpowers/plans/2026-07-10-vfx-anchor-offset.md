# VFX Anchor Offset Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the original world-space displacement between a VFX origin and its live entity anchor.

**Architecture:** Extend `VfxCue` with a serialized `Vec3 anchorOffset`. Anchored server cues compute `origin - anchor.position()`, and client resolution returns `liveAnchor.position() + anchorOffset`, retaining the existing immutable-origin fallback after despawn.

**Tech Stack:** Java 21, Fabric 1.21.8 typed payloads, Mojang `Vec3`, Gradle JavaExec assertion tests.

## Global Constraints

- Work only in `D:/WorkFlow/Jujutsu Minecraft/.worktrees/nobara-cinematic-slice`.
- Follow `docs/superpowers/specs/2026-07-10-vfx-anchor-offset-design.md`.
- Add only world-space `anchorOffset`; do not add attachment enums, rotation, bones, or unrelated channel refactors.
- Preserve `cue.origin()` as fallback when the live anchor is missing.
- Use `Vec3.ZERO` for every unanchored cue.
- Use TDD and capture an expected RED before production edits.
- Do not use Computer Use or UI automation.

---

### Task 1: Add the failing anchor-offset contract

**Files:**
- Modify: `src/test/java/jujutsu/mod/vfx/VfxAnchorResolverTest.java`
- Modify: `src/test/java/jujutsu/mod/vfx/VfxCueTest.java`

**Interfaces:**
- Produces: the desired seven-field `VfxCue` constructor and resolver behavior.

- [ ] Add `Vec3 anchorOffset` immediately after `anchorEntityId` in every test cue construction.
- [ ] Replace the live-anchor assertion with an eye-height case: origin `(0, 1.62, 0)`, offset `(0, 1.62, 0)`, moved anchor `(10, 64, 10)`, expected `(10, 65.62, 10)`.
- [ ] Keep a missing-anchor case with a non-zero offset and assert fallback to the immutable origin.
- [ ] Add `anchorOffset` to the shared-field assertions and use a non-zero offset in the payload round-trip test.
- [ ] Run `.\gradlew.bat testVfxAnchor testVfxCore --no-daemon` from PowerShell.

Expected RED: test compilation fails because the production `VfxCue` constructor does not yet accept `anchorOffset`.

---

### Task 2: Implement offset transport and resolution

**Files:**
- Modify: `src/main/java/jujutsu/mod/vfx/VfxCue.java`
- Modify: `src/main/java/jujutsu/mod/vfx/VfxAnchorResolver.java`
- Modify: `src/main/java/jujutsu/mod/network/VfxCuePayload.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java`
- Modify: `src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java`
- Modify: `src/test/java/jujutsu/mod/vfx/VfxTimelineTest.java`
- Test: `src/test/java/jujutsu/mod/vfx/VfxAnchorResolverTest.java`
- Test: `src/test/java/jujutsu/mod/vfx/VfxCueTest.java`

**Interfaces:**
- `VfxCue(ResourceLocation effectId, Vec3 origin, int anchorEntityId, Vec3 anchorOffset, int intensity, long startGameTime, long seed)`.
- `VfxAnchorResolver.resolve(cue, lookup)` returns `cue.origin()` without a live anchor, otherwise `lookupResult.add(cue.anchorOffset())`.

- [ ] Add the `anchorOffset` record field after `anchorEntityId`.
- [ ] Serialize and deserialize `anchorOffset` immediately after `anchorEntityId` using `writeVec3`/`readVec3`.
- [ ] Change resolver live-anchor behavior to `anchor.add(cue.anchorOffset())`.
- [ ] Update all unanchored test/runtime constructors to pass `Vec3.ZERO`.
- [ ] Change Nobara's anchored cue helper overloads from raw `int anchorEntityId` to `Entity anchor` and construct:

```java
new VfxCue(effectId, origin, anchor.getId(), origin.subtract(anchor.position()),
        Math.max(1, intensity), gameTime, level.random.nextLong())
```

- [ ] Pass `player`/`caster` at the four anchored call sites instead of `.getId()`.
- [ ] Run focused GREEN:

```powershell
.\gradlew.bat testVfxAnchor testVfxCore testVfxTimeline --no-daemon
```

Expected: all three assertion mains pass and Gradle ends with `BUILD SUCCESSFUL`.

- [ ] Run `git diff --check`, review only the intended code/test files, then commit:

```powershell
git add -- src/main/java/jujutsu/mod/vfx/VfxCue.java src/main/java/jujutsu/mod/vfx/VfxAnchorResolver.java src/main/java/jujutsu/mod/network/VfxCuePayload.java src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkNobaraRuntime.java src/main/java/jujutsu/mod/character/nobara/projectjjk/ProjectJjkRitualRuntime.java src/test/java/jujutsu/mod/vfx/VfxAnchorResolverTest.java src/test/java/jujutsu/mod/vfx/VfxCueTest.java src/test/java/jujutsu/mod/vfx/VfxTimelineTest.java
git commit -m "fix(vfx): preserve entity anchor offsets"
```

---

### Task 3: Update documentation and close verification

**Files:**
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/04-client-vfx/VFX-core.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Public-api-surface.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/05-reference/Claim-Source-Index.md`
- Modify: `Jujutsu Kaizen/jujutsumod-codebase-codex/06-maintenance/How-to-add-next-character.md`
- Modify: `docs/session-handoffs/2026-07-10-vfx-core-implementation-handoff.md`

**Interfaces:**
- Documents the seven-field cue contract, offset construction rule, fallback behavior, verification evidence, and final commit ledger.

- [ ] Update the VFX Core shared-contract table and anchor paragraph with source-backed line citations.
- [ ] Update the public authoring example to pass `Vec3.ZERO` for unanchored cues and show the anchored offset formula in prose.
- [ ] Update the next-character checklist so anchored cues record `origin.subtract(anchor.position())`.
- [ ] Update affected claim-index anchors after final line-number discovery with scoped `rg -n`.
- [ ] Run:

```powershell
.\gradlew.bat check --no-daemon
.\gradlew.bat build --no-daemon -x test
git diff --check
```

- [ ] Copy `build/libs/jujutsumod-1.0.0.jar` over `D:/Games/instances/Jujutsu/mods/jujutsumod-1.0.0.jar` and compare SHA-256.
- [ ] Perform one read-only review of the implementation/spec range; fix only confirmed findings and rerun covering tests.
- [ ] Append exact RED/GREEN, build, review, jar-hash, no-Computer-Use, and remaining manual-QA evidence to the canonical handoff.
- [ ] Commit documentation/handoff as small conventional commits and finish with a clean worktree.
