# Session Handoff — Client GameTest foundation (issue #42 Stage B)

## Status

- Branch: `test/client-gametest-foundation` (from `main` @ `9926f7d`, the squash of Stage A PR #59); PR open — commits (a) `test(gametest): enable client GameTests in the isolated test mod`, (b) `test(gametest): add client load and observation canaries` (red proof recorded in the body), (c) `ci(gametest): add manual client GameTest evidence lane`, (d) `docs: record the Stage B client GameTest lane`.
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
- Green, client lane (stability runs): 5 consecutive `gradlew runClientGameTest --no-daemon` runs, all exit 0, durations 31s/27s/26s/27s/27s; `screenshots/0000_observation_canary.png` produced on every run (329198/299560/307844/324361/306230 bytes); zero leaked `KnotClient` java processes after each run (`jps -l`); zero hangs.
- Green, server lane + gate: `gradlew runGameTest` exit 0 ("3 GAME TESTS COMPLETE … All 3 required tests passed"), `gradlew qualityGate` exit 0 (documentation audit passed, metrics unchanged incl. `verification_programs: 29`; `verifyAssertionsEnabled: 29`; `auditReleaseJarIsolation: 1318 entries scanned, no test-mod or dev-only content`), `gradlew assemble` exit 0.
- Red proof, client canary (temporary mutation, restored — recorded in the canaries commit body): inverting the observation type assert (`observed.getType() == EntityType.PIG` → `!=`) made `runClientGameTest` FAIL with exit 1 and the logical diagnostic `[clientObservationCanary @client tick 53] client-observed entity type: expected <minecraft:pig>, actual <entity.minecraft.pig>`; restored → green (exit 0). Red proof, audit rule (recorded in the wiring commit body): a temporary `net/fabricmc/fabric/api/client/gametest/v1/red-proof-marker.txt` jar entry made `auditReleaseJarIsolation` FAIL listing "(test-only fabric client gametest API)"; removed → green.
- Jar inspection (manual, independent of the audit): `jar tf` on `build/libs/jujutsumod-1.0.0.jar` — 1318 entries, zero `gametest`-named entries, exactly one `fabric.mod.json` (id `jujutsumod`, entrypoint keys `client` + `main` only), zero ArchUnit/client-gametest API classes.
- Xvfb/CI experiment: GitHub registers `workflow_dispatch` workflows only from the default branch, so the manual lane cannot be dispatched until this PR merges — recorded as a lane property, not a failure. The Linux run itself was proven from a throwaway branch (temporary push trigger, branch deleted after the run): Actions run 31094422326 on ubuntu-24.04 + Xvfb completed SUCCESS in 2m31s, and its `client-gametest-evidence` artifact (326 KB) carries the client logs plus the headless `0000_observation_canary.png` with the pig centered under the crosshair. One green run proves the lane works headless; it is not yet a stability series — the lane stays manual and non-required either way.

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
- Commit split (main): (a) build wiring, (b) client canaries with red proof in body, (c) CI experiment lane, (d) docs handoff filled with block-5 facts.
