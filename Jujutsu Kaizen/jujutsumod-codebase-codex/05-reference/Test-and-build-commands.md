# Test and Build Commands

Status: CURRENT

## The one command

```bash
./gradlew qualityGate --no-daemon
```

On Windows use `gradlew.bat`. Nothing may be called verified without a green run of exactly this task (VERIFIED — build.gradle:682-683 "The one command"). CI runs this same task on push/PR (VERIFIED — .github/workflows/build.yml runs `./gradlew qualityGate --no-daemon`), so a green local run and a green CI run mean the same thing.

What the gate runs:

- `check` — compiles both source sets, runs the Gradle `test` task (JUnit 5), every JavaExec verification program, and the server GameTest lane (`runGameTest` is wired into `check`). GameTest scenarios live in `src/gametest/` (`ServerGameTests` entry, `TodoAimedSwap*` / `TodoStone*` / `NobaraAbilityResult*` suites plus fixtures, and the `client/` canaries incl. `SdfGlassCanaryTest`).
- `auditDocumentation` — `tools/audit_docs.py`: current-docs set, local links, Codex metrics vs the source tree.
- `verifyAssertionsEnabled` — fails if any verification JavaExec task would run without `-ea` (VERIFIED — build.gradle:526).
- `auditReleaseJarIsolation` — the release jar carries no test-mod content (VERIFIED — build.gradle:603).

For a clean proof rather than an up-to-date result, add `--rerun-tasks`. The gate does not build the player-facing jar; that is `./gradlew assemble --no-daemon` → `build/libs/jujutsumod-<version>.jar` (`mod_version` in `gradle.properties`).

What a green gate proves — and does not: shape, contracts, pure logic, plus two neutral GameTest canaries on a headless `ServerLevel`. It proves nothing about ability feel, rendering, or in-world behavior, which stay manual (E1 in `docs/KNOWN_ISSUES.md`).

## Focused loop

```bash
./gradlew check --no-daemon
./gradlew runGameTest --no-daemon
./gradlew runClientGameTest --no-daemon
./gradlew auditDocumentation --no-daemon
python3 tools/audit_docs.py
```

Single-test tasks follow the `test<Name>` pattern (e.g. `testCharacterPlayerState`, `testCharacterDefinitions`, `testNobaraAbilitySlots`, `testTodoProfile`); `./gradlew verifyAssertionsEnabled` reports the live verification-program inventory. New tests are JUnit 5 by default — they join `check` with no registration. The client GameTest lane (`runClientGameTest`) renders frames and screenshots but is not part of the gate; its CI counterpart (`client-gametest.yml`) is a manual `workflow_dispatch` experiment by design.

## MCP dev lane (live game)

The modded client is driven over MCP (`mc-world` on 127.0.0.1:8765, `mc-client` on 8766; bearer token from the mod config). Full recipe lives in the `mcp-lane-launch` project skill — read it, do not reconstruct from memory:

```bash
gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=<upstream-mcp-jar> --no-daemon
```

Design and tool contract: `docs/MCP_DEV_CONTROLS.md`, `docs/MCP_1_21_8_PORT_SPIKE.md`. GameTest is the deterministic red/green; MCP is live exploration and screenshots; a bug found live becomes a GameTest scenario.

## Sandbox notes

Java 21 only. If Gradle fails with PKIX errors, import the sandbox proxy CAs into the JDK trust store; request network only for domains a real failure names. Full recipe: `docs/BUILDING_IN_SANDBOX.md`, which also owns the client-smoke checklist.
