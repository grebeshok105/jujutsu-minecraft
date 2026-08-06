# Session Handoff — Server GameTest Foundation (issue #42 Stage A)

## Status

- Branch: `test/server-gametest-foundation` (from `main` @ e209b34), PR #59 open — commits `48534ed` (harness + canaries), `12a8860` (jar audit), `c25982d` (CI evidence), `e9b54fb` (docs), plus the review-adjudication commit; CI green.
- Stage A adds: the `gametest` source set (Loom `createSourceSet`), the isolated test-mod `jujutsumod-gametest` with two server canaries, `runGameTest` wired into `check` by Loom (so it rides into `qualityGate`), the release-jar isolation audit `auditReleaseJarIsolation` on `qualityGate`, and a CI step that preserves gametest reports/logs on failure.
- Explicitly NOT in Stage A: the client side of issue #42 (client GameTest, runClient automation), the real world/ability scenarios tracked in issue #21, and the MCP/control-surface work of issue #43.

## Design contract

- Source set `gametest` → `src/gametest/java` + `src/gametest/resources` (created by Loom via `createSourceSet = true`).
- Test-mod id `jujutsumod-gametest`; descriptor `src/gametest/resources/fabric.mod.json`; entrypoint `fabric-gametest` → `jujutsu.mod.gametest.ServerGameTests`.
- Canaries: `serverLoadsProductionMod(GameTestHelper)` and `neutralEntityLifecycle(GameTestHelper)`; static helpers only in `jujutsu.mod.gametest.GameTestFixtures`.
- JUnit XML report: `build/test-results/gametest/junit.xml` (via `fabric-api.gametest.report-file` on the `gameTest` run config). Logs/crash-reports: `build/run/gameTest/logs/`, `build/run/gameTest/crash-reports/`.
- Merge gate: Loom auto-wires `runGameTest` into `check`, and `auditReleaseJarIsolation` is an explicit `qualityGate` dependency next to `auditDocumentation`; a red canary or a jar leak fails the canonical gate.
- The release jar must never contain `jujutsu/mod/gametest/` entries, the test-mod descriptor, or gametest structures.

## Verification

Canonical gate (POSIX form; `.bat` equivalent on Windows):

```bash
./gradlew qualityGate --no-daemon --max-workers=1 --no-watch-fs
```

Focused commands while working:

```bash
./gradlew runGameTest --no-daemon
./gradlew auditReleaseJarIsolation --no-daemon
python tools/audit_docs.py
```

Evidence recorded 2026-08-06 (Windows checkout, `gradlew.bat`):

- Task graph proof: `gradlew tasks --all` lists `runGameTest - Starts the 'gameTest' run configuration` (the actual Loom 1.17.17 name — capital T; older docs saying `runGametest` are wrong for this setup). `gradlew check --dry-run` contains `:runGameTest SKIPPED`; `gradlew qualityGate --dry-run` contains `:runGameTest`, `:check`, `:remapJar`, and `:auditReleaseJarIsolation` — the server suite and the jar audit are inside the canonical gate, no second CI command list.
- Green: `gradlew qualityGate assemble --no-daemon` → `BUILD SUCCESSFUL`, exit 0; inside it `runGameTest` ran the headless GameTest server (`========= 3 GAME TESTS COMPLETE ... All 3 required tests passed`), `verifyAssertionsEnabled: 29 verification JavaExec tasks all enable assertions`, `auditReleaseJarIsolation: jujutsumod-1.0.0.jar: 1318 entries scanned, no test-mod or dev-only content`.
- JUnit XML at `build/test-results/gametest/junit.xml` names both canaries: `jujutsumod-gametest:server_game_tests_server_loads_production_mod`, `jujutsumod-gametest:server_game_tests_neutral_entity_lifecycle` (plus vanilla `minecraft:always_pass`).
- Red proof, canary: `helper.assertEntityPresent(EntityType.PIG, relativeSpawn)` in `neutralEntityLifecycle` temporarily inverted to `assertEntityNotPresent` → `Task :runGameTest FAILED`, java exit value 1, diagnostic `Did not expect Pig to exist at ... (relative: 3, 1, 3) on tick 0`; the same mutation took `gradlew qualityGate` to `BUILD FAILED` (failure reaches the gate). Assertion restored; suite green again.
- Red proof, jar audit: temporary `from sourceSets.gametest.output.classesDirs` in the `jar` task → `Task :auditReleaseJarIsolation FAILED` (after a successful `remapJar`), gradle exit 1, listing `jujutsu/mod/gametest/ServerGameTests.class` and `GameTestFixtures.class` as leaked entries. Mutation removed; audit green again.
- Manual jar inspection (independent of the audit): 1318 entries, zero `gametest`-named entries, exactly one `fabric.mod.json` with id `jujutsumod`, entrypoint keys `client` + `main` only, no `jujutsu/mod/{bridge,mcp,control}/`, no ArchUnit or fabric gametest API classes.

## Manual smoke checklist

The two Stage A canaries prove the harness, not gameplay. Everything below stays manual (tracked in E1, scenarios in issue #21):

- Real player↔mob and player↔player swap, blocked destinations, second-teleport failure and rollback, motion/rotation/fall-distance preservation, the packet path end to end.
- Feint clap vs real clap indistinguishability to a second player.
- Pair swap selection lifecycle against a live world — expiry, marked-body death, dimension change, STRICT cancellation moving nobody.
- Stone lifecycle — flight, collision vanish, lifetime expiry, `V` self-swap, `Shift+V` target swap, STRICT refusal moving nobody.
- Triple cycle — three-body preflight refusal moving nobody, and rollback restoring every moved body on a mid-commit failure.
- Client-only surface (stage B of issue #42): renderer, mixin, packet, UI, HUD, sound.

## Delivered implementation notes

- Build wiring (Block 1): `fabricApi.configureTests` with `createSourceSet = true`, `modId = 'jujutsumod-gametest'`, `enableClientGameTests = false`; the `gameTest` run config writes `fabric-api.gametest.report-file` → `build/test-results/gametest/junit.xml`; CI uploads gametest reports/logs/crash-reports on failure.
- Test mod (Block 2): `ServerGameTests` (two canaries), `GameTestFixtures` (static helpers only), descriptor per contract — no mixins, no client entrypoints, static version `"1.0.0"`.
- Jar audit (Block 4): `auditReleaseJarIsolation` scans the remapped jar for test-mod classes/descriptors, dev-only package prefixes, gametest data/structures, and test-only libraries; fails with a per-entry list.
- Docs (this handoff, Block 3): E1 reworded but still open; `docs/BUILDING_IN_SANDBOX.md` Focused verification gained `runGameTest`/`auditReleaseJarIsolation`; AGENTS.md green-gate paragraph updated minimally.
- Commit split (main): (1) gametest wiring + canaries with red-proof in body, (2) jar audit with its red-proof, (3) CI artifacts, (4) docs handoff.
