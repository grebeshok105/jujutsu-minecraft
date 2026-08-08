# Session Handoff — Post-merge main (Aug 7-8 wave complete)

## State of main

- Branch: `main`, HEAD `4dd5729` (post-#67). Everything merged, zero open PRs.
- All work from the Aug 6-8 wave is in main:
  - #62 spike MCP bridge on 1.21.8 (upstream port, 105+ tools)
  - #63 dev-control MCP tools + autonomous world entry (7 jujutsu_* tools)
  - #64 ability-result contract (AbilityResult tri-state)
  - #65 Todo stone lifecycle GameTests (21 scenarios, 29/29 green)
  - #66 L3 completion (ticks_wait, rotation_set, fixture_list)
  - #67 draggable ability cooldown HUD for all vessels
- Earlier wave: #59-61 (GameTest infra + Todo aimed swap GameTests).

## What exists now

- MCP dev lane: `src/mcpdev` companion, gated by `-PmcpSpike`/`-PmcpUpstreamJar`; 11 jujutsu tools; quickPlay autonomous entry (`prepareMcpSpikeRun`).
- GameTest lane: server + client, 29 scenarios green, `runGameTest` in qualityGate.
- HUD: `AbilityHud` (SDF/MSDF), `hudSlots()` + `maxCooldownTicks()` seams on `CharacterClientDefinition`, drag via DragHandler + GLFW polling.
- AbilityResult: `jujutsu.mod.character.AbilityResult` — SUCCESS / HANDLED_FAILURE / UNHANDLED_FAILURE.

## Open issues (8)

- #18 localization parity (hardening, easy)
- #22 static runtime state lifecycle owner (medium)
- #26 transient radius VFX delivery (medium, client)
- #23 ClickGui SDF profiling (UNVERIFIED)
- #24 residual shared-state debt (low)
- #25 vessel classes into packages (low)
- #56 brainstorm Todo velocity swap (parked)
- #45 VFX authoring engine (PARKED, do not implement)

## Rule-of-four artifacts

All pipeline state lives in `D:/WorkFlow/jujutsu-minecraft/.omp/rule-of-four/` (8 pipelines: ability-hud, ability-result, client-gametest-b, mcp-dev-controls, mcp-l3, mcp-port-spike, todo-aimed-swap, todo-stone). Each has plan-spec, block reports, review-spec, evidence.

## Verification

- `./gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs` — green on `4dd5729` (post-hotfix a394643 + HUD).
- Live MCP proof: autonomous entry, vessel select/invoke/state/reset over MCP, HUD frames captured (`.omp/rule-of-four/ability-hud/hud_*.png`).

## Next candidates

1. New vessel (Yuji / Maki — add-vessel skill)
2. #18 localization parity (quick win)
3. #26 VFX delivery polish
4. #21 remaining slices (Mega Nail, Shadow Drop ceiling, pair/triple atomicity)
