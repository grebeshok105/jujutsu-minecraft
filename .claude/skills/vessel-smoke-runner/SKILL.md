---
name: vessel-smoke-runner
description: Use when running an automated in-game smoke test of a vessel kit (Nobara / Todo / Megumi) through the MCP bridge — manifest of expected casts, state-delta oracles, targeting, evidence frames, three-section report. Proven on the Aug 2026 overnight smoke (43 cases, 0 real failures).
---

# Vessel Smoke Runner

Automated in-game smoke of a vessel kit over MCP, no human hands. Proven on the Aug 6-7 2026 overnight run (43 cases, 20 PASS / 19 confirmed refusals / 0 real failures; report in `.omp/smoke-run-1/report.md`).

## Preconditions

- Client running with MCP lane: `gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=<jar>` (see `mcp-lane-launch` skill). Readiness = `listening at http://127.0.0.1:8765` in the log.
- `mc-world` (8765) and `mc-client` (8766) MCP servers available to the agent.
- Player UUID: offline-mode = md5 name-UUID of `OfflinePlayer:<name>` (uuid v3 shape); name changes every dev-client boot — read it from `client_status` first.
- Vessel selected via `jujutsu_vessel_select`; cooldowns cleared via `jujutsu_cooldowns_clear` BEFORE every case.

## Manifest (never blind-run all slots)

Classify every (vessel, slot) pair BEFORE casting:

- `FREE` — casts without a target (summon, self-resonance, feint, stone throw, shadow free step, nail trap)
- `NEEDS_TARGET` — spawn a target with upstream `entity_summon` (+ `NoAI:1b` via `data merge entity`) and aim with `jujutsu_player_set_rotation` (server-side yaw/pitch; upstream `entity_teleport` does NOT rotate the player — use vanilla `/tp` via `command_execute` or the jujutsu rotation tool)
- `EXPECTED_REFUSAL` — the same aimed slot cast with NO target: refusal must arrive (tests the refusal path; goes in the "confirmed refusals" section, never "failures")
- `RECORD-ONLY` — no deterministic oracle; record observations

## Oracles (never trust `routed:true` alone)

`routed:true` proves the router accepted, not that the effect happened. Verify via state deltas:

- `jujutsu_state_get` deltas: pack non-null, stone true, trap true, position changed, cooldowns > 0 (cooldown delta is the universal success signal — a successful cast starts one, a refusal does not)
- upstream `entity_get` for entity positions/health
- `view_capture` frames as secondary evidence, never the oracle
- `routed:true` with zero state delta = YELLOW flag in the report, not a pass

## Timing

- Shadow Move resolves ~6-8 ticks after `routed:true` (sink animation) — measure positions no earlier than 12 ticks post-cast
- Stone lifetime 100 ticks at 0.23 blocks/tick; waits must exceed the flight
- `fixture_reset` kills Megumi's pack — test summon→recall in one pair without a reset between

## Targeting

- Aim "nowhere" = pitch −30 toward the sky, or the previous target stays under the crosshair (silent mis-aim)
- Clear cooldowns per case, not per pair
- Nobara kit is item-gated: hammer melee needs the hammer in the main hand (`item replace entity <p> weapon.mainhand with jujutsumod:straw_doll_hammer`)
- Nail trap needs the crosshair on the GROUND (not a target); consumes 3 nails; starts NO cooldown (observed — nail cost is the price)

## Report

Three sections, so triage never drowns in expected refusals:

1. **Real failures** — anything not explained by harness error; each first-attempt failure must be re-examined (timing / mis-aim / uncleared cooldown) before recording as a mod bug
2. **Yellow flags** — routed:true with no delta; design questions (e.g. hairpin with no marked nails: routed:true but zero effect)
3. **Confirmed refusals + passes** — per-case with the observed delta

Save frames under `.omp/smoke-run-<n>/`; write `report.md` there.
