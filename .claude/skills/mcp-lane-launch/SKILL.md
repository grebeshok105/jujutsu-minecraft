---
name: mcp-lane-launch
description: Use when starting, restarting, or checking the MCP dev lane for the Jujutsu Minecraft mod — launching the modded client with the MCP bridge (runClient -PmcpSpike), autonomous world entry, bearer-token sync, readiness checks, and clean shutdown. Also use when the MCP servers (mc-world / mc-client) are not reachable.
---

# MCP Lane Launch

Start the modded Minecraft client so an agent can drive the game over MCP (vessel select, ability invoke, state reads, screenshots). Proven end-to-end Aug 2026 (PRs #62/#63/#66).

## Launch

From any worktree that has the mcpdev source set (spike lineage or main after #63):

```bash
gradlew.bat runClient -PmcpSpike \
  -PmcpUpstreamJar=D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/minecraft-fabric-mcp-1.1.0+1.21.8.jar \
  --no-daemon --max-workers=1 --no-watch-fs --console=plain
```

- Set `JAVA_TOOL_OPTIONS=-Xmx3G` (the client default heap can OOM natively alongside the agent's own processes — observed Aug 7)
- `prepareMcpSpikeRun` (wired as runClient dependency inside the `-PmcpSpike` gate) idempotently seeds `run/saves/mcp-spike/` and pre-writes `pauseOnLostFocus:false` + `tutorialStep:none` into `run/options.txt` — the client boots straight into the world with no menu interaction

## Readiness

Wait for BOTH in the process log:

```
MCP server listening at http://127.0.0.1:8765 (auth=...)
MCP client server listening on http://127.0.0.1:8766
```

112 tools registered (105 upstream + 7 jujutsu) on the server endpoint. `tools/list` on 8765 is the ground truth.

## Bearer token

- First boot creates `run/config/minecraft_fabric_mcp/config.json` with `bearer_token` (and `auth_required`). If the config is absent the server starts `auth=false` — requests work without a header until a config exists.
- For agent use: export `MC_MCP_TOKEN=<bearer_token>` in the environment where the OMP daemon resolves `${MC_MCP_TOKEN}` (global `~/.omp/agent/mcp.json` reads it at startup). The token rotates when the mod re-writes its config — re-sync before long sessions.

## Global MCP wiring

`~/.omp/agent/mcp.json` already declares:

```json
"mc-world":  { "type": "http", "url": "http://127.0.0.1:8765/mcp", "headers": { "Authorization": "Bearer ${MC_MCP_TOKEN}" } },
"mc-client": { "type": "http", "url": "http://127.0.0.1:8766/mcp" }
```

Project-level `.omp/mcp.json` exists too but discovery is cwd-based — the global file is the guaranteed path.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Port 8765 refused | client not running or not past world load | check process log for `listening`; the server binds on world load, not at boot |
| 401 on every call | config has auth_required=true but token env missing/stale | re-sync MC_MCP_TOKEN from `run/config/minecraft_fabric_mcp/config.json` |
| `listening` then crash | native OOM | relaunch with `JAVA_TOOL_OPTIONS=-Xmx3G`; check free RAM first (agent processes + WSL can eat 26/32 GB) |
| world missing → DisconnectedScreen | `run/saves/mcp-spike/` absent | run `gradlew.bat prepareMcpSpikeRun -PmcpSpike -PmcpUpstreamJar=<jar>` |
| empty body from curl on Windows | MSYS curl `-d` quoting | probe with python urllib instead |

## Shutdown

Stop the client process (hub `stop` or Ctrl+C). Ports 8765/8766 release on shutdown; MCP server mod has no linger. Never leave a client running unattended across sessions — it holds 3+ GB RAM and the token rotates on restart.
