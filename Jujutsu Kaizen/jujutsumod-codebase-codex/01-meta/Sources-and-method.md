# Sources & Method

← [[00-MOC]]

## Primary sources

1. Worktree: `D:/WorkFlow/Jujutsu Minecraft/.worktrees/nobara-cinematic-slice` (branch `codex/nobara-cinematic-slice`)
2. Repo: `AGENTS.md`, `docs/`
3. ProjectJJK research vault: `Jujutsu Kaizen/grok-projectjjk-codex/` (parity only)
4. Code graph: codebase-memory-mcp project for cinematic worktree

## MCP

| Tool | Result |
|---|---|
| `codebase-memory-mcp__list_projects` | found cinematic project |
| `codebase-memory-mcp__index_repository` mode=`full` | reindexed: **14076** nodes, **17331** edges |
| Obsidian `mcpvault` | **unavailable** this session (0 tools) — filesystem vault used |

## Non-MCP

- Full Java inventory (77 files)
- Line dumps: entrypoints, registries, profile, networking, runtimes, nail entity, client networking
- `build.gradle` verification tasks
- Asset counts under `assets/jujutsumod`
- Checkout vs worktree size compare

## Fully / deeply read

`JujutsuMod`, `JujutsuModClient`, `fabric.mod.json`, `gradle.properties`, `build.gradle`, all registries, `JujutsuNetworking` + payload records, full `ProjectJjkNobaraProfile`, method maps for `ProjectJjkNobaraRuntime`, `ProjectJjkRitualRuntime`, `ProjectJjkNailEntity`, marks/link managers, selection manager, client impulse handlers, mixins json, `en_us.json`.

## Not verified this pass

- `runClient` / multiplayer feel
- Frame-perfect VFX/audio mix
- Every PNG/GLSL byte semantics
- Full body of legacy `NobaraHairpinRuntime` / non-ProjectJJK item classes (present, secondary)

## Date

2026-07-08

---
tags: #jujutsumod #method
