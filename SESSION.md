# Session Handoff — MCP dev-control surface (issue #43 slice 2)

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/mcp-port-spike`
- Branch: `feat/mcp-dev-controls` (base = spike head `f31f9cb`; stacks onto `spike/mcp-1.21.8-upstream-port`, PR #62, unmerged)
- Scope: v0 L3 control/observation surface (7 `jujutsu_*` MCP tools in the dev-only mcpdev companion) + autonomous world entry for the spike runClient. No gameplay changes; production gains only small public accessors on existing runtime classes.

## Design contract

- Committed design: [docs/MCP_DEV_CONTROLS.md](docs/MCP_DEV_CONTROLS.md) (decision-record format; committed before implementation per the brainstorming gate).
- Spike facts remain owned by [docs/MCP_1_21_8_PORT_SPIKE.md](docs/MCP_1_21_8_PORT_SPIKE.md).
- Tool surface, accessor signatures, fixture-reset order, and the quickPlay recipe are frozen in the design doc's tables — implementation must not drift from them.

## Verification

- Gate: `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` (covers main-side accessors + their JUnit tests; mcpdev is outside the gate by construction).
- Tool compile proof: `./gradlew.bat jarMcpdev -PmcpUpstreamJar=D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/minecraft-fabric-mcp-1.1.0+1.21.8.jar`.
- Autonomous entry: `./gradlew.bat prepareMcpSpikeRun -PmcpSpike -PmcpUpstreamJar=<same>` then `runClient -PmcpSpike -PmcpUpstreamJar=<same>` — must reach the world and bind 8765 with no manual input.
- Live proof (the word "verified" for tool behavior): one OMP session — status → vessel_select → ability_invoke → state_get → cooldowns → fixture_reset → view_capture → clean shutdown.

## Status

- IN PROGRESS: rule-of-four pipeline; 4 workers implementing blocks 1-4 (accessors, control tools, observe/reset tools, autonomy), main integrating + live proof (block 5).
- This handoff replaces the spike handoff; update the Status/Next lines at integration.

## Next steps

1. Integrate worker blocks, run the verification ladder above, capture live-proof evidence.
2. Push branch, open the stacked PR onto the spike branch, comment on issue #43.
3. Upstream PRs (SSE hold-open, Jackson compatibility, ToolProvider seam) remain a separate follow-up.
