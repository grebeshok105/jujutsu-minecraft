# MCP dev-control surface — design (issue #43 slice 2)

Status: CURRENT
Date: 2026-08-06
Issue: [#43](https://github.com/grebeshok105/jujutsu-minecraft/issues/43) (slice 2, L3 v0), stacked on the port spike ([docs/MCP_1_21_8_PORT_SPIKE.md](MCP_1_21_8_PORT_SPIKE.md), PR #62)

## Goal

Close the autonomous dev loop: an agent launches the modded client, lands in a world with no human input, selects a vessel, casts abilities, observes authoritative state, resets the fixture, and captures evidence — all over the MCP bridge the spike proved.

## Decisions

1. **Seven tools, not the full L3 surface.** Issue #43's "~12-18 tools" describes the eventual L3 orientation; v0 ships the minimal closed loop: `jujutsu_vessel_list`, `jujutsu_vessel_select`, `jujutsu_ability_invoke`, `jujutsu_cooldowns_get`, `jujutsu_cooldowns_clear`, `jujutsu_fixture_reset`, `jujutsu_state_get`. Everything else (fixtures for specific scenarios, entity helpers) already exists upstream (105 tools) or waits for a proven need.
2. **All tool code stays in the dev-only companion** (`src/mcpdev`, gated by `-PmcpUpstreamJar`/`-PmcpSpike`; `auditReleaseJarIsolation` keeps it out of the release jar). Production gains only small public accessors on existing runtime classes — no new packages, no behavior changes.
3. **Production invocation path, not a parallel one:** `CharacterSelectionManager.select` → `CharacterAbilityExecutor.tryCast` — the exact chain the network receiver uses past its claim gate (the tool's optional `expect_vessel` mirrors that gate). GameTest fixtures already rely on the same entry (`TodoSwapTestFixtures.castPrimary`).
4. **`fixture_reset` clears runtime combat state only** — never selection, starter claims, or inventory; never applies cooldowns (a new `TeardownReason.FIXTURE_RESET` maps to no cooldown for Megumi's pack teardown). Steps run best-effort in a fixed order (cooldowns → stagger → Todo → Megumi → Nobara → shared tags/effects) so one failure cannot mask the rest; the Nobara sweep includes the resonance time-dilation release — without it the server stays in slow-mo.
5. **Autonomous entry is vanilla quickPlay, not UI automation:** `--quickPlaySingleplayer mcp-spike` on the Loom `client` run config (spike gate only), a `prepareMcpSpikeRun` task that idempotently seeds `run/saves/mcp-spike/` (copy of an existing save) and pre-writes `pauseOnLostFocus:false` + `tutorialStep:none` into `run/options.txt`. Verified against the 1.21.8 mapped client jar: a missing world is NOT created (DisconnectedScreen), so the task warns when no source save exists.
6. **The companion stays `environment: "*"`** (matches upstream); every tool fail-closes when the server is absent or the player UUID is offline.
7. **Verification split stays honest:** `qualityGate` never compiles mcpdev, so tool code is compile-proven by `jarMcpdev -PmcpUpstreamJar=...` and behavior-proven by one live OMP session (launch → world → select → cast → observe → reset → screenshot → shutdown); main-side accessors get JUnit coverage inside the normal gate.

## Tool surface (v0)

| Tool | Kind | Args | Result |
|---|---|---|---|
| `jujutsu_vessel_list` | read | — | roster ids |
| `jujutsu_vessel_select` | write | `player_uuid`, `vessel_id` (canonical id, case-insensitive; typos refused) | previous + selected |
| `jujutsu_ability_invoke` | write | `player_uuid`, `slot` (CharacterAbility name), `expect_vessel?`, `notify?` | `routed`, vessel, cooldown after |
| `jujutsu_cooldowns_get` | read | `player_uuid` | 10 slots, remaining ticks |
| `jujutsu_cooldowns_clear` | write | `player_uuid`, `slot?` | cleared slot or all vessels/slots |
| `jujutsu_fixture_reset` | write | `player_uuid` | per-step outcomes |
| `jujutsu_state_get` | read | `player_uuid` | vessel, position, stagger, cooldowns, mod effects, Todo pair/stone, Megumi pack/trap/move/drop, Nobara nails (current dimension only; the reset sweeps all levels) /marks |

Names are snake_case in the established `jujutsu` tool domain (registered by the existing provider; the upstream category map already accepts it).

## Non-goals

Scenario DSLs, golden-image comparison, MCP tools in the production jar, dedicated-server hardening beyond fail-closed checks, and any gameplay change. The `~12-18` L3 surface grows only when a concrete scenario demands a tool.
