# Session Handoff — MCP 1.21.8 upstream port spike (issue #43)

## Active branch

- Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/mcp-port-spike`
- Branch: `spike/mcp-1.21.8-upstream-port` (base `main` `dac76da`, post-#61)
- Scope: feasibility spike ONLY — port `chapmanjw/minecraft-java-fabric-mcp-server` (`0caf461`) to Minecraft 1.21.8, prove a live OMP connection, prototype the repo-specific extension seam, keep everything development-only. No L1–L5 implementation, no input control, no multiplayer.

## Verdict

**PASS** — architecture **A (port/fork upstream)**, fallback B (selected-module reuse). The full table, every finding and all evidence live in [docs/MCP_1_21_8_PORT_SPIKE.md](docs/MCP_1_21_8_PORT_SPIKE.md) — that decision record is the single owner of the spike's facts; this handoff only points at it.

## What this branch carries (our repo side)

- `src/mcpdev/` — dev-only companion mod `jujutsumod-mcpdev` (ToolProvider + `jujutsu_mod_status` + server-holding bridge). Dormant without `-PmcpUpstreamJar`; loaded into runClient only with `-PmcpSpike`.
- `build.gradle` — mcpdev source set/jar wiring (all tasks skip propertyless), the `-PmcpSpike` run knob (`modLocalRuntime` upstream jar + `localRuntime` companion jar), `auditReleaseJarIsolation` extended with MCP prefixes + descriptor entrypoint check (red-proven).
- `.gitignore` — `.omp/` (project-scoped OMP MCP config stays uncommitted).
- `docs/THIRD_PARTY_NOTICES.md` — upstream MIT entry (dev-only, never shipped).
- `docs/MCP_1_21_8_PORT_SPIKE.md` — the decision record (the task's original research-directory path is audit-forbidden here).

The upstream port itself (1.21.8 target, ~100 conditional rename sites, SSE hold-open, Jackson `fields()` fix, ToolProvider seam, 8 seam tests) lives in the upstream fork — link in the PR and issue #43.

## Verification

- `./gradlew.bat qualityGate` green on final content (metrics unchanged — mcpdev is outside every audit glob); `assemble` green propertyless; release jar 1319 entries, zero MCP/companion content, no `mcp-tools` entrypoint.
- Upstream fork: `:1.21.8:build` green (458 tests), `:1.21.11:test` green (sibling target intact).
- Live: three OMP 17.2.5 sessions (initialize `2025-03-26` → `2025-06-18` echo accepted), 105 tools, read+mutation+revert, client_status/sense/view_capture (854×480 real frame), ports released on shutdown.
- Red proofs recorded: protocol (one-shot SSE revert → stream test fails) and isolation (fake `com/chapmanjw` entry → audit fails).

## Next steps

1. The next implementation slice is written at the end of the decision record (v0 L3 surface through the seam; reproduce the PR #61 aimed-swap scenario over MCP).
2. Propose the three version-agnostic fixes upstream (SSE hold-open, Jackson compatibility, ToolProvider seam) to shrink the fork delta.
3. PR for this branch stays open for review — do NOT merge without the user's explicit call.
