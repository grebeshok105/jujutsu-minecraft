---
name: mcp-lane-launch
description: Use when starting, restarting, or checking the MCP dev lane for the Jujutsu Minecraft mod — launching the modded client with the MCP bridge (runClient -PmcpSpike), autonomous world entry, bearer-token sync, readiness checks, and clean shutdown. Also use when the MCP servers (mc-world / mc-client) are not reachable.
---

# MCP Lane Launch

Start the modded Minecraft client so an agent can drive the game over MCP (vessel select, ability invoke, state reads, screenshots). Proven end-to-end Aug 2026 (PRs #62/#63/#66).

## Home

The lane now runs from the main repository (`D:/WorkFlow/jujutsu-minecraft`, branch lineage with mcpdev sources — `feat/dev-lane-home` and descendants); the old second-clone spike worktree (`D:/WorkFlow/Jujutsu Minecraft/.worktrees/mcp-port-spike`, Aug 2026) is retired. `run/saves/mcp-spike` is already seeded here.

Known dev-lane traps (verified 2026-09-09):

- The singleplayer player UUID changes on every client launch (offline profile) — always query `entity_query @a` first, then use the returned uuid.
- A new tool class in `src/mcpdev` must be added to `JujutsuModStatusToolProvider.toolClasses()` or it never reaches the live registry.
- `jujutsu_ticks_wait` without `player_uuid` waits exactly N ticks; with `player_uuid` it exits when the player's PRIMARY cooldown clears (readiness gate), capped at N.
- `jujutsu_vessel_select` takes `vessel_id` (not `vessel`); the tool answers with `previous:` / `selected:` lines.
- Camera aiming: `jujutsu_player_set_rotation` sets the server-side rotation only and never moves the client camera. `entity_teleport` with a `facing` point moves both the client camera and the server-side rotation, so it also satisfies server-side look resolvers (verified: `PRIMARY_SNEAK` sic returned `routed:true` immediately after a teleport-facing, with no `player_set_rotation`). The only real failure mode is aiming at the wrong thing — check what the camera points at before invoking an aimed ability.
- Screenshot sessions drift into night: call `command_execute` `time set noon` before a capture series, or every frame comes back as a dark blob.
- Summoned pets (Divine Dogs) wander around their owner: read the subject's position, teleport the camera to face it and capture in ONE call — any modelling wait lets it leave the frame.
- Helmet-less zombies burn to death at noon and vanish mid-test; summon `Invulnerable:1b` / `PersistenceRequired:1b` targets (y=-59 in the dev world) when a test needs a lasting victim.
- Judge frames numerically (mean luminance, silhouette bbox, region diffs); 854x480 vision reads are unreliable for mob detail. Crop tight and aim before asking a vision model anything.

Known traps from the shikigami lane (verified 2026-09-12):

- **Ability slots on the wire** (`MegumiAbilityRouter`, not the keybinds): summon/recall/swap = `PRIMARY`, sic = `PRIMARY_SNEAK`, shikigami cycle = `TERTIARY_SNEAK`. `SECONDARY*` is the shadow trap/step branch — pressing it returns `routed:false` and says nothing about the feature you meant to drive.
- **`facing` pitch is inverted from intuition.** `entity_teleport` passes the point to vanilla `/tp … facing`, and MC xRot is positive-down: a facing point *below* your eye (e.g. `target.y - 0.6` to "aim at the feet") yields a **negative** pitch, i.e. the camera looks up. Pass a point *above* the subject, read `client_status.pitch` back, and iterate until the pitch is positive (+15..+40 for a subject 2.5–4 blocks away) before spending a sic.
- **`sense_crosshair` / `sense_raycast` return `MISS` for entity targets even when the crosshair is on one** (observed next to a frame that plainly shows the zombie centred). Oracles that do work: the invoke's `routed:` flag, the cooldown charge, and the target's HP delta.
- **Aim math is fine once the orbit is right**: from a correct pitch the sic resolves and `routed:true` appears immediately, with `cooldown_remaining_ticks: 30`. A `routed:false` with `0` cooldown is an aim/resolve failure, never an ability bug.
- **`jujutsu_fixture_reset` resets the shikigami selection to DOGS**, so cycle explicitly (`TERTIARY_SNEAK` until `state_get` reads `selected:`), and read `megumi.shikigami.selected` / `type` around every summon and sic — a sic pressed while DOGS is selected routes to the *dog* runtime and silently does nothing for the new bodies.
- **To observe a stationary animation, freeze the body with `entity_apply_effect … minecraft:slowness, amplifier 8`** (the brain keeps ticking, the legs stop). Re-assert it in the same cell as the measurement: the effect expires, the body then walks off, and `velocity 0` from an earlier read means nothing.
- **`entity_apply_effect` takes `uuid` and no `dimension`** (`additionalProperties:false` makes the extra key fail the whole call). `entity_summon` / `entity_teleport` take `position` and `facing` as objects. `entity_kill` takes a `uuid`; for selectors use `command_execute` with `/kill @e[...]`.
- **`view_capture` costs ~0.3 s**, which is the same order as a 6-tick action window: burst densely, then compare frames numerically (a changed-pixel mask localizes the subject; a vision read on a ~40–150 px subject is unreliable, and the arena's slimes and wild frogs get mis-read as your subject). Clear the noise first (`command_execute kill @e[type=minecraft:slime,distance=..64]`).
- **A stationary subject needs the camera and the aim on opposite sides.** One direction can serve both when the subject sits *between* the camera and the target: place the target beyond the body along the sight line — the player's own summon is skipped by the resolver (`isOwnBody`), and the body lands in the foreground of the same shot.

Known traps from the bugfix lane (verified 2026-09-12, issues #76–#85):

- **`hub start` does not inherit `JAVA_HOME`.** Launched via the supervisor, `gradlew.bat` picks the machine's Java 17 and loom refuses to configure (`Dependency requires at least JVM runtime version 21`), so the lane dies in 4 s. Always pass `env: {"JAVA_HOME": "C:/Users/KOMP1/scoop/apps/temurin21-jdk/current"}`.
- **One MCP session per process.** A fresh `initialize` per tool call trips the lane's rate limit after a handful of calls (`initialize failed 429`). Drive the lane from one Python process that imports the throwaway client once (`analysis/mc_mcp.py` caches `_sessions`); the one-line CLI is only for single calls.
- **`view_capture` answers with base64, it does not write a file.** The result carries `{"type":"image","data":…}`; decode it yourself (`.superpowers/rule-of-four/bugfix-76-85/analysis/shot.py`). Asking for a `path` argument returns nothing useful.
- **`entity_query` prints a YAML list** — the id line is `- uuid: <uuid>`, not `uuid: <uuid>`; parsers that miss the dash silently see zero entities.
- **The lane player is mortal and a dead player breaks every probe silently.** A `walking_bed` killed Player770 mid-pass; afterwards `entity_get` answers `Entity not found`, mobs stop aggroing and HP reads come back empty — symptoms that look like a broken feature. Recover with the `computer` tool: window `Minecraft* 1.21.8` → screenshot → vision model locates the Respawn button → click; then keep `command_execute effect give @a resistance 600 1..3 true` on while parking mobs nearby.
- **Aggression is proven by damage, not by proximity.** For any "the mob attacks the owner" premise, check the owner's HP trajectory (or the attacker's HP delta once the pack answers); a zombie that merely stands nearby may never have set `getTarget()`.

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

## Devin VM / Linux lane (verified 2026-09-23)

The lane works identically on a Linux box — verified end-to-end on a Devin cloud VM (Ubuntu, Temurin 21, client launched and driven through both MCP endpoints).

- Launch (bash): `JAVA_TOOL_OPTIONS=-Xmx3G ./gradlew runClient -PmcpSpike -PmcpUpstreamJar=<upstream-jar> --no-daemon --max-workers=1 --no-watch-fs --console=plain` with `JAVA_HOME` on JDK 21 — a VM's default java can be 17, and loom then refuses to configure (same trap class as the hub JAVA_HOME one).
- The client renders on the session's real desktop (`:0`) when one exists — no xvfb needed; on a headless box wrap in `xvfb-run` yourself or let loom do it. `view_capture` works either way — the client-sense endpoint never needs a real display to be watched.
- **A fresh clone has no `run/saves/` at all** — `prepareMcpSpikeRun` warns "neither run/saves/mcp-spike nor run/saves/New World exists" and quickPlay lands on "Failed to Quick Play". One-time fix per VM: in the client window create a world named exactly `mcp-spike` with *Allow Commands: ON* (save folder becomes `run/saves/mcp-spike/`) — every later launch quick-plays straight in. The task's built-in copy path only fires when `run/saves/New World` already exists. To skip even that GUI pass, a seeded save ships in-repo at `.claude/skills/mcp-lane-launch/mcp-spike-world.tar.gz` (~5.5 MB, vanilla 1.21.8 world, Allow Commands ON): `mkdir -p run/saves && tar -xzf .claude/skills/mcp-lane-launch/mcp-spike-world.tar.gz -C run/saves`.
- With no `run/config/minecraft_fabric_mcp/config.json` yet both servers start `auth=false` — no bearer needed locally. The pack's `run-config-mcp/config.json` (auth_required=false, rate_limit_rpm 2000) can be dropped into `run/config/minecraft_fabric_mcp/` for a fixed config.
- Readiness is unchanged: `MCP server listening at http://127.0.0.1:8765` binds on world load (125 tools = 105 upstream + 20 `jujutsu_*`), `8766` binds at boot (6 client-sense tools). Probe with python `urllib`: `initialize` → `notifications/initialized` → `tools/list`, carrying the returned `mcp-session-id` header on every later call.
- One MCP session per process still applies — drive the lane from a single cached session, not one-shot `initialize` per call.
