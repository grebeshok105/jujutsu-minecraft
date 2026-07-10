# Nobara Combat Expansion Implementation Plan

> **For agentic workers:** Execute this plan task-by-task with test-first changes and small conventional commits. Perform one global review after all implementation stages.

**Goal:** Extend the canonical ProjectJJK Nobara implementation with persistent anchored nails, per-nail Hairpin damage, hammer combat, Black Flash, curse links, self resonance, and synchronized first/third-person presentation.

**Architecture:** `ProjectJjkNailEntity` remains the persistent nail carrier and gains typed anchors resolved by stable identity. Gameplay remains server-authoritative; transient presentation continues through `VfxCue -> VfxDirector -> NobaraVfxRecipes`. Shared combat timing, balance, Black Flash focus, stagger, and curse links are narrow reusable server systems.

**Tech Stack:** Java 21, Fabric 1.21.8, GeckoLib 5, typed custom payloads, existing Java assertion tasks.

## Global Constraints

- Work only in `.worktrees/nobara-cinematic-slice`; do not alter the dirty main checkout.
- Do not restore legacy Nobara/Hairpin managers or per-effect networking/render callbacks.
- Temporary entity/chunk unload must preserve UUID-backed anchors and curse links.
- All gameplay numbers live in the central Nobara profile and are initial balance constants.
- Inventory-contained items are out of scope; entity-backed and registered runtime objects are supported.
- Manual in-game feel QA belongs to the user; build/startup evidence must not be presented as gameplay proof.

---

### Task 1: Design, balance, and shared timing

- [ ] Record the approved design and plan in the worktree.
- [ ] Add failing assertions for centralized combat constants and action timing.
- [ ] Add the minimum balance/timeline types and make the assertions pass.
- [ ] Run focused tests and commit `docs(nobara): design combat expansion` / `feat(nobara): centralize combat timing`.

### Task 2: Persistent typed nail anchors

- [ ] Add failing tests for entity UUID rebinding, temporary unavailability, confirmed removal, block invalidation, runtime-object resolution, and save/load fields.
- [ ] Add `NailAnchor`, resolution state, registry/resolver, and integrate them into `ProjectJjkNailEntity`.
- [ ] Keep unloaded anchors dormant and rebind by stable id; discard only on confirmed removal or invalid block replacement.
- [ ] Prepare one nail every 10 ticks up to eight, each as a real server entity.
- [ ] Run focused anchor/entity tests, compile, and commit.

### Task 3: Per-nail Enlarge and Boom

- [ ] Add failing tests proving 1/4/8 nail selection, independent activations, and removal of fixed mark scaling.
- [ ] Add the Hairpin damage type/tag that bypasses vanilla damage cooldown.
- [ ] Make Enlarge/Boom enumerate concrete owned nails across entity, block, and runtime anchors.
- [ ] Emit one damage event and one VFX cue per nail, consuming only successfully activated nails.
- [ ] Run focused tests, `check`, runtime build, and commit.

### Task 4: Hammer combat and stagger

- [ ] Add failing tests for contextual action routing and server validation.
- [ ] Implement the narrow shared stagger/action-interrupt state.
- [ ] Replace unconditional LMB explosive launch with horizontal/overhead/prepared-nail/embedded-nail routing.
- [ ] Schedule gameplay from the shared timeline and keep embedded nails available to Hairpin.
- [ ] Run focused tests, compile both source sets, and commit.

### Task 5: Black Flash and focus

- [ ] Add failing tests for early/on-time/late CE input, supported impact kinds, multiplier, no Hairpin activation, and persistent focus.
- [ ] Implement server-owned timing windows and reusable focus state.
- [ ] Treat repeated LMB during a window as CE input; otherwise continue the contextual combo.
- [ ] Add stable VFX ids/recipes for successful Black Flash.
- [ ] Run focused tests, `check`, runtime build, and commit.

### Task 6: Curse links and self resonance

- [ ] Add failing tests for explicit links only, unload persistence, owner removal, one-link auto-use, multi-link refusal, and stale selection rejection.
- [ ] Implement immutable `CurseLink`, registry API, explicit lifecycle, and development commands.
- [ ] Add typed link-list/selection payloads and a compact multi-link selection screen.
- [ ] Implement Shift+R behavior and `6/18` self/enemy damage with strong stagger.
- [ ] Run focused server/client tests and commit.

### Task 7: Resonance, animations, and VFX polish

- [ ] Add failing assertions for `28` resonance damage, absence of Weakness/Slowness, VFX registration, and shared timing.
- [ ] Strengthen doll impact and target reaction through existing VFX Core channels.
- [ ] Add synchronized first/third-person animations for all new actions while preserving held-item bones.
- [ ] Use Blockbench MCP only through the mandatory Blockbench skill flow if source animation editing requires it.
- [ ] Run client compilation, VFX assertions, `check`, runtime build, and commit.

### Task 8: Documentation, global review, and delivery

- [ ] Update the versioned Obsidian/codebase codex notes for nail lifecycle, Nobara runtime, curse links, networking, VFX, APIs, and uncertainties.
- [ ] Run one global technical/spec review over the complete implementation range.
- [ ] Fix every confirmed Critical/Important finding in one correction pass; do not request a second review.
- [ ] Run focused tests, `check`, runtime build, and `git diff --check`.
- [ ] Copy the final runtime JAR into the game instance, replace the older mod JAR, and compare SHA-256.
- [ ] Commit the final docs/review fixes and write the session handoff.
