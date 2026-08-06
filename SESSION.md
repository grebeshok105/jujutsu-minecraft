# Session Handoff — Client GameTest foundation (issue #42 Stage B)

## Status

- **PR #60 is MERGED** into `main` as squash commit `fa86c4c` (2026-08-06): <https://github.com/grebeshok105/jujutsu-minecraft/pull/60>. The squash carries the whole rule-of-four wave — 4 scouts → 5-block plan → 4 workers → 4 independent reviewers (zero P0/P1, one P2, twelve P3s; all adjudicated, fixes applied). Branch commits inside the squash: (a) build wiring, (b) client canaries (red proof in body), (c) manual CI evidence lane, (d) docs handoff, (e) review fixes (red proof v2 in body).
- Stage B adds: `enableClientGameTests = true` in the isolated test mod's `fabricApi.configureTests` (Loom creates the `clientGameTest` run config and the `runClientGameTest` task, group `fabric`, deliberately NOT attached to `check`); the modid filter `fabric.client.gametest.modid=jujutsumod-gametest`; the `fabric-client-gametest` entrypoint with two client canaries (`ClientLoadCanaryTest`, `ClientObservationCanaryTest`) in `jujutsu.mod.gametest.client`; shared client fixtures `ClientGameTestFixtures`; the release-jar audit extended to the fabric client gametest API prefixes; a manual `workflow_dispatch` CI experiment lane with evidence upload; docs updated (E1, build-time-gate limits, BUILDING_IN_SANDBOX, AGENTS.md green-gate paragraph).
- Explicitly NOT in Stage B: the real gameplay scenarios of issue #21, the MCP/control-surface work of issue #43, issue #45, pixel/SSIM/golden gating, and making the client lane required — it is deliberately non-required and outside `qualityGate`.

## Design contract

- Entrypoint key `fabric-client-gametest`; interface `net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest` — `void runTest(ClientGameTestContext context)`; entrypoint array order IS execution order (load canary first).
- Canaries: `jujutsu.mod.gametest.client.ClientLoadCanaryTest` (client env + production mod presence, no world) and `ClientObservationCanaryTest` (singleplayer world via `context.worldBuilder().create()`, neutral `Pig` spawned on the server thread, client-side observation, `observation_canary` screenshot at 854x480, removal sync, try-with-resources cleanup). Fixtures contract in `ClientGameTestFixtures` (`diagnostic`, `assertWithDiagnostic`, `clientTick`, `waitForEntityVisible`, `waitForEntityGone`; `PRE_WORLD_TICK = -1`). All waits have finite timeouts; no `Thread.sleep`, no `Minecraft.getInstance()` from the test thread.
- Task `runClientGameTest` (group `fabric`) is Loom-generated and NOT wired into `check` (Loom attaches only the server `runGameTest`); run dir `build/run/clientGameTest/` is wiped before every run by `deleteGameTestRunDir`. Artifacts: `logs/`, `crash-reports/`, `saves/`, `screenshots/%04d_<name>.png` (observation canary → `0000_observation_canary.png`).
- NO machine-readable client report exists in fabric-api 0.136.1 (source-verified): the lane's result is the task exit code; the server lane's JUnit XML at `build/test-results/gametest/junit.xml` stays server-only.
- Audit: `auditReleaseJarIsolation` category 4 extended to `net/fabricmc/fabric/api/client/gametest/` and `net/fabricmc/fabric/impl/client/gametest/` ("test-only fabric client gametest API"); the release jar must keep zero gametest content.
- Client test code lives in the `gametest` source set under `jujutsu/mod/gametest/client/` (audit prefix preserved); the compile classpath already sees `net.minecraft.client.*` and `fabric-client-gametest-api-v1` (probe-verified, no flip needed).

## Verification

Canonical gate (POSIX form; `.bat` equivalent on Windows):

```bash
./gradlew qualityGate --no-daemon --max-workers=1 --no-watch-fs
```

Focused commands while working:

```bash
./gradlew runGameTest --no-daemon
./gradlew runClientGameTest --no-daemon
./gradlew auditReleaseJarIsolation --no-daemon
python tools/audit_docs.py
```

Evidence recorded 2026-08-06 (Windows checkout `.worktrees/client-gametest-foundation`, `gradlew.bat`, AMD Radeon RX 6700 XT):

- Task graph proof: `gradlew tasks --all` diff against the pre-flip baseline gained exactly `runClientGameTest` ("Starts the 'clientGameTest' run configuration") and `deleteGameTestRunDir`; `gradlew check --dry-run` and `gradlew qualityGate --dry-run` both contain `:runGameTest` and zero `runClientGameTest` occurrences — the client lane is outside the canonical gate by construction, and the gate composition is unchanged.
- Green, client lane (stability runs): 5 consecutive `gradlew runClientGameTest --no-daemon` runs, all exit 0; durations 31s/26s/26s/26s/26s per each run's `BUILD SUCCESSFUL in …` line (bash wall-clock measured ~1s higher on runs 2-5); `screenshots/0000_observation_canary.png` produced on every run (299-330 KB, sizes recorded via `stat` at run time — `deleteGameTestRunDir` wipes the run dir before each run, so only the latest screenshot survives on disk); zero leaked `KnotClient` java processes after each run (`jps -l`); zero hangs. The post-review fix wave added three more lane runs: green fix verification (29s), the red-proof run, and the green restore (27s).
- Green, server lane + gate: `gradlew runGameTest` exit 0 ("3 GAME TESTS COMPLETE … All 3 required tests passed"), `gradlew qualityGate` exit 0 (documentation audit passed, metrics unchanged incl. `verification_programs: 29`; `verifyAssertionsEnabled: 29`; `auditReleaseJarIsolation: 1318 entries scanned, no test-mod or dev-only content`), `gradlew assemble` exit 0. The gate is re-run green on the final content immediately before each docs/fix commit, and the PR's CI `qualityGate` run re-proves it on the committed HEAD.
- Red proof, client canary (temporary mutation, restored — recorded in the review-fix commit body): inverting the observation type assert (`observed.getType() == EntityType.PIG` → `!=`) fails the lane with the logical diagnostic `[clientObservationCanary @client tick 46] client-observed entity type: expected <minecraft:pig>, actual <minecraft:pig>` — the inverted assert fires precisely because the client DOES observe the pig; the gradle task fails (`Task :runClientGameTest FAILED`, gradle exit 1) after the client JVM ends via the documented crash-propagation path (`Process 'command … java.exe' finished with non-zero exit value -1073740791` / NTSTATUS 0xC0000409, persisted in the run log); restored → green (exit 0). Red proof, audit rule (recorded in the wiring commit body): a temporary `net/fabricmc/fabric/api/client/gametest/v1/red-proof-marker.txt` jar entry made `auditReleaseJarIsolation` FAIL listing "(test-only fabric client gametest API)"; removed → green.
- Jar inspection (manual, independent of the audit): `jar tf` on `build/libs/jujutsumod-1.0.0.jar` — 1318 entries, zero `gametest`-named entries, exactly one `fabric.mod.json` (id `jujutsumod`, entrypoint keys `client` + `main` only), zero ArchUnit/client-gametest API classes.
- Xvfb/CI experiment: GitHub registers `workflow_dispatch` workflows only from the default branch, so the manual lane cannot be dispatched until this PR merges — recorded as a lane property, not a failure. The Linux run itself was proven from a throwaway branch (temporary push trigger, branch deleted after the run): Actions run 31094422326 on ubuntu-24.04 + Xvfb completed SUCCESS (job duration 2m29s per the GitHub API), and its `client-gametest-evidence` artifact (326350 bytes) carries the client logs plus the headless `0000_observation_canary.png` with the pig centered under the crosshair. One green run proves the lane works headless; it is not yet a stability series — the lane stays manual and non-required either way.
- Post-merge: with the workflow file on the default branch, the lane dispatched normally — `workflow_dispatch` run 31098479281 from `main`, SUCCESS (job ~2m18s), making the Xvfb lane 2/2 green on ubuntu-24.04.

## Manual smoke checklist

The Stage B canaries prove the harnesses (server + client boot, client observes server state, screenshot evidence lands), not gameplay. Everything below stays manual (tracked in E1, scenarios in issue #21):

- Real player↔mob and player↔player swap, blocked destinations, second-teleport failure and rollback, motion/rotation/fall-distance preservation, the packet path end to end.
- Feint clap vs real clap indistinguishability to a second player.
- Pair swap selection lifecycle against a live world — expiry, marked-body death, dimension change, STRICT cancellation moving nobody.
- Stone lifecycle — flight, collision vanish, lifetime expiry, `V` self-swap, `Shift+V` target swap, STRICT refusal moving nobody.
- Triple cycle — three-body preflight refusal moving nobody, and rollback restoring every moved body on a mid-commit failure.
- Client-only surface: renderer, mixin, packet, UI, HUD, sound. The client GameTest lane proves boot and observation only; visual/gameplay feel stays on the client-smoke checklist.

## Delivered implementation notes

- Build wiring (Block 1): `enableClientGameTests = true` + Stage A comment rewritten; `loom.runs.named('clientGameTest')` modid filter; `fabric-client-gametest` entrypoints in the test-mod descriptor; audit category 4 extended.
- Client fixtures + load canary (Block 2): `ClientGameTestFixtures` (exact public contract as specced) + `ClientLoadCanaryTest`.
- Observation canary (Block 3): `ClientObservationCanaryTest` — world, entity sync, screenshot evidence, removal sync, layered cleanup.
- CI lane (Block 4): `.github/workflows/client-gametest.yml` — `workflow_dispatch`-only, xvfb install, `runClientGameTest`, evidence upload `if: always()`, no `continue-on-error`.
- Docs (this handoff, Block 4): E1 rewritten but still open; "Limits of the build-time gate" amended; `docs/BUILDING_IN_SANDBOX.md` client-lane paragraph; AGENTS.md green-gate paragraph updated minimally; SESSION.md replaced with this Stage B handoff.
- Review wave (4 independent reviewers — wiring / fixtures / observation / CI+docs+integration): zero P0/P1; one P2 (stability durations quoted from wall-clock instead of the build logs — corrected above) and twelve P3s, all adjudicated; applied fixes: `waitForEntityVisible` post-wait null guard (contract can no longer return null silently), `waitForEntityGone` null-level semantics documented, "protected" wording, mid-body `lookAt` aim (crosshair centers the pig at any distance), registry-key `actual` in the type diagnostic (symmetric `minecraft:pig` rendering), two scene-input javadoc bullets (camera aim, counter-prefixed artifact name), evidence-precision corrections in this handoff. Deferred by adjudication: jar-ROOT/nested-jar audit coverage (pre-existing documented jar-in-jar scope limit, revisit only if `include` bundling appears).
- Commit split (main): (a) build wiring, (b) client canaries with red proof in body, (c) CI experiment lane, (d) docs handoff filled with block-5 facts, (e) review fixes with red proof v2 in body.
