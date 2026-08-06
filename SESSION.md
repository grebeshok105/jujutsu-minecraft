# Session Handoff — Todo aimed swap server GameTests (issue #21 slice 1)

## Status

- Branch `test/todo-aimed-swap-gametest` (base `main` `7f9f5de`, post-#60; base CI green). PR: opened from this branch into main (the number lands in the post-merge close-out); review wave PRE-AUTHORIZED (4 reviewers: seam+unit / fixtures+scenarios 1-2 / scenarios 3-4 / docs+integration-honesty); do NOT self-merge; do NOT close #21.
- This slice adds the first real gameplay coverage of issue #21: 4 server GameTest scenarios for Todo aimed Boogie Woogie (PRIMARY) against the production runtime, a production-neutral commit-teleport seam with a fast unit test pinning its default wiring, shared swap fixtures, and the entrypoint registration for the two new test classes.
  - `TodoAimedSwapGameTests`: successful player↔mob swap with the momentum window and cooldown; no-target refusal charging nothing.
  - `TodoAimedSwapRollbackGameTests`: blocked-destination atomic refusal (real STRICT collision geometry); forced second-placement failure rolling both bodies back (via the seam).
  - `SwapCommitTeleport` seam + `SwapCommitTeleportTest` (src/test, plain JUnit 5).
- Explicitly NOT in this slice (honest remainder, tracked in issue #21):
  - player↔player swap (needs #42's approved second-player strategy);
  - Todo's SOFT exact-point arrival fallback (arrival into geometry) — only the non-consenting target's STRICT refusal is covered;
  - passenger, vehicle, leash, spectator, removed/dead, cross-level, range, and line-of-sight gates against real entities — only the no-target path exercises the real resolver; the remove-before-commit revalidation branch is deterministically unreachable (the forced-failure seam covers the same failure surface instead);
  - the 20-block range limit and line-of-sight refusal variants;
  - observer/client presentation (clap, afterimage, arrival, endpoint ribbon, HUD, motion updates) — client lane, not in this slice;
  - the pre-#57 marker system is not tested (deleted in #57).

## Design contract

- Production invocation: `CharacterAbilityExecutor.tryCast(player, CharacterAbility.PRIMARY, true)` — the exact server-side call `JujutsuNetworking.handleCharacterAbility` makes (scout-1 §1-2). The C2S payload receiver itself is private and requires a live client connection, so the executor is the closest production entry point for a server GameTest; this justification is recorded in the PR body. Tests NEVER reimplement swap logic; scenarios NEVER call `SafeBodyPlacement`/`TargetResolver` directly.
- Player fixture: vanilla `helper.makeMockServerPlayerInLevel()` ONLY (real registered ServerPlayer, loopback `EmbeddedChannel` connection, full `placeNewPlayer` path). CREATIVE game mode is harmless (TodoSwapGates checks spectator/alive/transport/stagger/hands only). Arena entry via the 8-arg `player.teleportTo(level, x, y, z, Set.of(), yaw, pitch, false)` (connection-free; sets yRot/yHeadRot/xRot itself). Cleanup via `server.getPlayerList().remove(player)` — `discard()` leaves PlayerList entries.
- Seam contract (`jujutsu.mod.character.todo.SwapCommitTeleport`, @FunctionalInterface, `boolean teleport(LivingEntity, ServerLevel, Vec3, float, float)`): production-neutral; default wiring = `PRODUCTION_COMMIT_TELEPORT` (the same 8-arg authoritative teleport `place()` uses); rollback and restore NEVER route through the seam; `overrideCommitTeleport` requires non-null; the override MUST be restored via `restoreProductionCommitTeleport()` in `finally` (static, process-global); default pinned by `SwapCommitTeleportTest` (assertSame on production instance, override/restore round-trip, null rejection).
- Test ids as they appear in JUnit XML (`build/test-results/gametest/junit.xml`, prefix `jujutsumod-gametest:`):
  - `todo_aimed_swap_game_tests_successful_swap_exchanges_bodies_and_grants_window`
  - `todo_aimed_swap_game_tests_no_target_refusal_charges_nothing`
  - `todo_aimed_swap_rollback_game_tests_blocked_destination_refuses_atomically`
  - `todo_aimed_swap_rollback_game_tests_forced_second_placement_failure_rolls_back_both_bodies`
- Fixtures cleanup contract: every test cleans up on success AND failure (try/finally or scheduled cleanup): PRIMARY cooldown cleared, momentum effect removed, transient state dropped (`TodoTransientState`), player removed via `server.getPlayerList().remove(player)`, mobs discarded with absence verified, seam override restored. Fresh mock player per test (random UUID) isolates cooldown/transient maps.
- Diagnostics phases vocabulary (extended Stage A diagnostic: fixture/phase @tick, caster UUID, target UUID, expected, actual): `setup / resolve / preflight / commit1 / commit2 / rollback / cleanup`.
- The swap is FULLY synchronous inside `tryCast` — cast and assert in the same scheduled callback; no multi-tick machinery. `@GameTest(maxTicks = 100, skyAccess = true)`, all geometry inside the default 8x8x8 structure, swap distances 4-7 blocks, coordinates helper-relative converted once via `helper.absolutePos`/`absoluteVec`.

## Verification

Canonical gate (Windows; POSIX form `./gradlew`):

```bash
gradlew.bat qualityGate --no-daemon
```

Focused commands while working:

```bash
gradlew.bat runGameTest --no-daemon
gradlew.bat runClientGameTest --no-daemon
gradlew.bat assemble --no-daemon
gradlew.bat auditReleaseJarIsolation --no-daemon
python tools/audit_docs.py
```

Evidence recorded 2026-08-06 (Windows checkout `.worktrees/todo-aimed-swap-gametest`, `gradlew.bat`):

- Determinism: FOUR consecutive green `runGameTest` runs after integration — 26s/24s/23s/23s per each run's `BUILD SUCCESSFUL in …` line, exit 0 each, every run `All 7 required tests passed` (2 Stage A canaries + vanilla always_pass + the 4 new aimed-swap tests), zero hangs, `jps -l` shows no leaked java processes after each run. Two more green runs followed the diagnostics polish (24s/24s, the second being the post-red-proof restore) — six greens total across the wave, no order dependence observed (each scenario owns a fresh mock player UUID and its own structure instance).
- Red proof (temporary mutation, restored — recorded in the scenarios commit body): the scenario-4 rollback assert `assertBodyState(..., "caster", caster, casterBefore)` was inverted to expect the PIG's snapshot for the caster → `runGameTest` FAILED (exit 1, "1 required tests failed") with the rollback-mismatch diagnostic `[forcedSecondPlacementFailureRollsBackBothBodies/rollback @tick 2 caster=<uuid> target=null] caster position: expected <pig snapshot> actual <caster origin>` — the failure is the logical rollback assertion (actual shows the caster correctly restored to its ORIGIN, proving the production rollback ran), not a timeout/crash; the JUnit XML names `jujutsumod-gametest:todo_aimed_swap_rollback_game_tests_forced_second_placement_failure_rolls_back_both_bodies`; the SAME mutation took `qualityGate` to `BUILD FAILED` on `:runGameTest FAILED` (the server suite rides `check`); restored → `runGameTest` green again.
- Gate + assemble on final content: `qualityGate` exit 0 (documentation audit passed — metrics `main_java: 125` after the seam interface and `test_java: 81` after the unit test, `verification_programs: 29` unchanged; `verifyAssertionsEnabled: 29`; `auditReleaseJarIsolation: 1319 entries scanned, no test-mod or dev-only content`), `assemble` exit 0 (`build/libs/jujutsumod-1.0.0.jar`).
- Client-lane regression (production `src/main` changed by the seam — one run): `runClientGameTest` exit 0, both Stage B client canaries green, `0000_observation_canary.png` produced.
- Jar isolation: manual `jar tf` — 1319 entries (was 1318; the new entry is `jujutsu/mod/character/todo/SwapCommitTeleport.class`, production API that SHOULD ship), zero `gametest`-named entries, zero `SwapCommitTeleportTest` entries.
- CI: <TBD-block5: GitHub Actions run ids for the PR branch — qualityGate (and assemble) green; client-gametest.yml is workflow_dispatch-only and untouched>.

## Manual smoke checklist

The four GameTests prove the server-side swap contract; everything below stays manual (tracked in #21):

- Real player↔player swap, blocked-destination small-nudge, and SOFT arrival into geometry with a live client.
- Passenger/vehicle/leash/spectator/dead/removed/cross-level gate behaviour against a live world.
- Range-limit and line-of-sight refusal messages; cooldown not consumed after refusals (automated for the server side, message text is client-side).
- Observer/client presentation: clap, afterimage, arrival, endpoint ribbon, HUD/cooldown state, motion updates.

## Delivered implementation notes

- Seam (Block 1): `src/main/java/jujutsu/mod/character/todo/SwapCommitTeleport.java` (public @FunctionalInterface; production-neutral; rollback/restore never routed through it) + minimal `TodoBoogieWoogieRuntime` edit (only the two aimed-swap commit calls rerouted; `place`/`restore`/`rollback`, pair and triple commits untouched) + `src/test/java/jujutsu/mod/character/todo/SwapCommitTeleportTest.java` (default wiring, override/restore round-trip, null rejection — no Minecraft bootstrap) + `Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md` metric `Test Java files | 80` → `| 81`.
- Fixtures + scenarios 1-2 (Block 2): `src/gametest/java/jujutsu/mod/gametest/TodoSwapTestFixtures.java` (frozen public contract: `POSITION_EPSILON`, `BodyState`, `diagnostic`, `setupTodoCaster`, `aimAt`, `castPrimary`, `assertBodyState`, `assertNoPrimaryCharge`, `cleanupCaster`) + `TodoAimedSwapGameTests.java` + `fabric.mod.json` entrypoint registration for BOTH new classes.
- Scenarios 3-4 (Block 3): `src/gametest/java/jujutsu/mod/gametest/TodoAimedSwapRollbackGameTests.java` — blocked-destination alcove (STRICT candidate-kill table in the class javadoc) and the seam-forced partial-commit rollback with a counting wrapper (first commit real, second reports failure; seam restored in `finally`).
- Docs (this handoff, Block 4): SESSION.md replaced; issue-21 comment and PR body drafted (applied by the main agent via `gh`); `docs/BUILDING_IN_SANDBOX.md` intentionally NOT edited — this slice adds no new command and no new fixture requirement (the seam is a plain Java interface, no build change; `makeMockServerPlayerInLevel` is an existing GameTestHelper API; all commands used already appear in the doc).
- Review wave (Phase 3): 4 pre-authorized reviewers; results consolidated into review-spec.md by the main agent; fixes re-verified; PR body updated.

## Commit split

- (a) refactor(todo): seam + `SwapCommitTeleportTest` + MOC metric bump;
- (b) test(gametest): fixtures + scenarios 1-2 + entrypoint registration;
- (c) test(gametest): scenarios 3-4 (red proof for scenario 4 recorded in this body);
- (d) docs: this handoff (placeholders filled), issue-21 comment and PR body posted via `gh`; #21 stays OPEN; no self-merge.
