# Test and Build Commands

Status: CURRENT

## Full verification

```bash
./gradlew build --no-daemon --rerun-tasks
python3 tools/audit_docs.py
git diff --check
```

The build defines custom JavaExec verification programs, each using a main method with assertions enabled by `-ea`. `check` dynamically depends on every one; `./gradlew verifyAssertionsEnabled` is the live inventory (VERIFIED — build.gradle). The standard Gradle test task remains part of build but is not the whole suite.

Focused commands:

```bash
./gradlew testCharacterPlayerState --no-daemon
./gradlew testCharacterDefinitions testCharacterClients --no-daemon
./gradlew testNobaraAbilitySlots testProjectJjkNobaraProfile testProjectSanity --no-daemon
./gradlew testTodoProfile testTodoSwapPlan testTodoTargetSafety testTodoHandsEmpty --no-daemon
./gradlew testTodoFakeClap testTodoPairSwap testTodoSwapMomentum --no-daemon
./gradlew testTargetResolver testClickGuiDrag --no-daemon
```

## MCP dev lane (spike lineage, dev-only)

```bash
./gradlew jarMcpdev -PmcpUpstreamJar=<upstream-mcp-jar>            # compile proof for the mcpdev companion (outside qualityGate)
./gradlew prepareMcpSpikeRun -PmcpSpike -PmcpUpstreamJar=<jar>     # seed run/saves/mcp-spike + options.txt keys (idempotent)
./gradlew runClient -PmcpSpike -PmcpUpstreamJar=<jar>              # boots straight into world mcp-spike; MCP on 127.0.0.1:8765/8766
```

The companion and every task above are dormant without the properties; the release jar stays MCP-free (`auditReleaseJarIsolation`). Recipe details: [docs/MCP_DEV_CONTROLS.md](../../../docs/MCP_DEV_CONTROLS.md); protocol facts: [docs/MCP_1_21_8_PORT_SPIKE.md](../../../docs/MCP_1_21_8_PORT_SPIKE.md).

Use runClient for UI, rendering, mixin, animation, combat-feel, and VFX claims. The docs audit is currently a required local PR check; wiring it into GitHub Actions needs workflow-write permission for the connected integration.
