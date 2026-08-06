# MCP 1.21.8 port spike — decision record

Status: COMPLETE — verdict **PASS**, architecture **A (port/fork upstream)**
Date: 2026-08-06
Issue: [#43](https://github.com/grebeshok105/jujutsu-minecraft/issues/43)

## Coordinates

| Component | Value |
|---|---|
| Our `main` SHA | `dac76da68df8370ef3ef6c510d58fd60eb61e31f` (post-PR #61) |
| Spike branch | `spike/mcp-1.21.8-upstream-port` |
| Upstream project | [`chapmanjw/minecraft-java-fabric-mcp-server`](https://github.com/chapmanjw/minecraft-java-fabric-mcp-server) |
| Upstream commit | `0caf461236b3b787b6e21f74cf61039d7b96fd5f` (post-`v1.1.0`) |
| Minecraft | `1.21.8` |
| Java | `21` (Temurin 21.0.11) |
| Fabric Loader | `0.19.3` (both projects already pin this) |
| Fabric API | `0.136.1+1.21.8` |
| Mappings | Mojang official (both projects) |
| Loom (ours) | `1.17.17` |
| Loom (inside upstream build) | `1.17.17` — requested first per the task rule and **worked immediately** (upstream pins `1.16-SNAPSHOT`; both ship the legacy `fabric-loom` plugin id) |
| Gradle (upstream) | wrapper pins 9.6.1; 9.5.1 actually ran on this machine (harmless quirk, README floor is 9.4) |
| OMP version | `17.2.5` (npm global, `%APPDATA%/npm/omp.cmd`) |
| OMP MCP protocol | `2025-03-26` initialize handshake; never validates the echoed server version |
| Upstream MCP protocol | `2025-06-18` (hardcoded; answers `initialize` unconditionally; `Mcp-Session-Id: stateless`) |
| Primary platform | Windows 11 |

## Verdict — PASS (all criteria met)

| Acceptance criterion | Result | Evidence |
|---|---|---|
| Upstream core compiles on 1.21.8 | **PASS** | `:1.21.8:build` BUILD SUCCESSFUL, 448→458 tests green, full mod (193 tools), zero functionality removed |
| Server endpoint in a real dev instance | **PASS** | `runClient -PmcpSpike` → 53 mods, `Registered 105 MCP tools`, `listening at http://127.0.0.1:8765` on world load |
| OMP connects over Streamable HTTP | **PASS** | three independent `omp -p` sessions against `.omp/mcp.json` (`type: http`); initialize 200 → `notifications/initialized` 204 → tools |
| tools/list works | **PASS** | 105 tools listed (104 upstream + `jujutsu_mod_status`) |
| ≥1 read tool | **PASS** | `server_get_status` (MC 1.21.8, loader 0.19.3, TPS 20, `registeredToolCount: 105`), `level_get_dimension_info`, `level_get_time`, `server_get_motd` |
| ≥1 bounded mutation | **PASS** | `entity_summon` pig → `entity_get` by returned UUID → `entity_despawn` → `entity_get` = `Entity not found`; wild-pig census identical before/after (2/2) |
| Rendered client endpoint | **PASS** | second in-process endpoint `127.0.0.1:8766` (6 inspection tools), `client_status` returns live in-world state |
| Real screenshot | **PASS** | `view_capture` → PNG image content block, **854×480**, 235,688 bytes, verified visually: the actual spike world frame (superflat, village house, first-person hand, hotbar, pause menu overlay from unfocused window) |
| Extension tool without protocol-gameplay coupling | **PASS** | `jujutsu_mod_status` via the new `mcp-tools` entrypoint returned `{mod_version: 1.0.0, selected_vessel: todo, vessel_source: CharacterSelectionManager}`; coupling direction mcpdev → {main, upstream}; production classes have zero MCP imports |
| Shutdown releases ports/resources | **PASS** | after stopping Minecraft: connect-refused on 8765/8766, `Get-NetTCPConnection` listener count 0 |
| Release jar free of MCP code | **PASS** | `qualityGate` + `assemble` green; 1319 entries, 0 matches for `com/chapmanjw/`, `io/modelcontextprotocol/`, `jujutsu/mcpdev/`; release descriptor has no `mcp-tools` entrypoint; audit extension red-proven |

## Compile findings (Stage 1)

Two error waves, both fully classified (details: rule-of-four `block-1-report.md`):

1. **Mapping renames (~100 errors, 9 adapter/impl files + EventWiring + ClientAccessImpl).** Mojang themselves renamed `ResourceLocation`→`Identifier`, `ResourceKey#location()`→`identifier()` and moved `world.level.GameRules`→`world.level.gamerules.GameRules` **between 1.21.8 and 1.21.11** (verified against Mojang tiny mappings: same obfuscated classes, no deprecated aliases). Fixed with a new stonecutter constant `mc_gte_21_11` and `//?` conditionals at import/use sites — the established upstream pattern, no business-logic version ifs.
2. **Real API drifts (second wave, a dozen sites):** `ServerLevel#getRespawnData` (1.21.11+) → `Level#getSharedSpawnPos` path; `GameRules#availableRules` went private → public `visitGameRuleTypes(GameRuleVisitor)`; `BiomeSpecialEffects#waterColor()` shape; `WorldBorder#getLerpTime/getSafeZone`. Each verified against the mapped 1.21.8 jar before fixing.

Non-blockers hit on the way: `maven.kikugie.dev` throttled mid-download (stonecutter 0.9.7 jar seeded into the Gradle cache from the plugin portal with SHA-1 verification); both scout-flagged Fabric API risks (`AttachmentRegistryImpl`, `fabric-message-api-v1`) exist in 0.136.1+1.21.8.

**No architectural blocker was found.** Error counts alone were meaningless — the whole port was renames plus a dozen genuine drift sites.

## Runtime findings (Stages 3–5)

Two live defects surfaced only at runtime — both are 1.21.8-specific and both now fixed + regression-tested:

1. **Jackson `ObjectNode#properties()` NoSuchMethodError.** Minecraft 1.21.8 ships Jackson **2.13.4.2** as a parent-classloader library (verified: `dependencyInsight` shows it directly on `runtimeClasspath`); it predates `properties()` (Jackson 2.15) and shadows upstream's bundled 2.22.1 (Knot is parent-first for non-mod packages). Every object-returning tool failed; scalar tools worked. Fix: 5 call sites moved to the version-agnostic `fields()` (Toon ×3, BlockTools, EventsTools). On 1.21.11+ the game's Jackson is newer, which is why upstream never saw this.
2. **Closed tool-category map rejected the extension domain.** `ToolCategory.forToolName` hard-throws for unknown domain prefixes, and `ToolCompatibilityFilter` evaluates every provider tool through it — `jujutsu_mod_status` was skipped at registration (`No category mapping for domain prefix 'jujutsu'`; registry stayed at 104). Fix: `ToolProvider.domainCategories()` (default empty) + a provider-domain registry in `ToolCategory` (built-ins non-overridable, collisions logged) + registration before filtering. Live re-run: `Registered 105 MCP tools`.

## OMP handshake transcript (Stage 4, token redacted)

```
POST /mcp initialize {"protocolVersion":"2025-03-26", ...}
  no auth  → 401 (fail closed)
  bearer   → 200, Mcp-Session-Id: stateless,
             result.protocolVersion = "2025-06-18",
             serverInfo = minecraft-java-fabric-mcp-server 1.1.0
POST /mcp notifications/initialized → 204
GET  /mcp (SSE) → ": open" immediately, ": ping" ~15 s, stream held open (sampled 20 s+)
```

- OMP sent `2025-03-26`, accepted the `2025-06-18` echo (it never validates the field) — **no dual-protocol layer needed**.
- `Mcp-Session-Id` present (constant `stateless`), OMP stores/echoes it; `initialize` + `notifications/initialized` both exchanged.
- **The one real concession (upstream-side): GET SSE hold-open.** Stock upstream returned a one-shot SSE frame and closed; OMP treats stream end as transport loss → reconnect loop → its circuit breaker (5 bursts/30 s) suspends the server. Fixed with dedicated `mcp-sse-*` daemon streams, 15 s heartbeat, `activeStreams` closed on stop (upstream's own architecture.md already promised streaming). Protocol constants untouched.
- Three independent OMP sessions connected/disconnected cleanly; no stale endpoint state.

## Server tool evidence (Stage 4)

- `server_get_status`: MC 1.21.8, loader 0.19.3, mod 1.1.0, TPS 20, MSPT ~5.4, `registeredToolCount: 105`, 3 dimensions.
- `jujutsu_mod_status`: `{mod_version: 1.0.0, selected_vessel: todo, vessel_source: CharacterSelectionManager}` — the live production selection read through the seam.
- `level_get_dimension_info` overworld: id/type/minY -64/maxY 319/timeOfDay.
- Mutation: `entity_summon` pig at player+3 → `entity_get` (UUID, exact position (12.5, -60, 9.5), health 10/10) → `entity_despawn` → `entity_get` = `Entity not found`; world census restored (2 wild pigs before and after).

## Client evidence (Stage 5)

- `client_status`: `in_game: true`, singleplayer, overworld, player position/rotation/health/food/held item — live state.
- `sense_entities` (r=16): empty, consistent with the world (wild pigs ~54 blocks away).
- `view_capture`: PNG content block, 854×480 (native framebuffer, `downscale=1`), 235,688 bytes, non-empty, visually verified as the real current frame (superflat spike world + village + first-person hand + hotbar; pause-menu overlay because the window was unfocused — vanilla singleplayer behavior, and proof that GUI and world share one honest framebuffer capture). Saved as uncommitted local evidence (`.omp/rule-of-four/mcp-port-spike/evidence/view_capture.png`).
- After client shutdown both ports refuse connections; zero listeners.

## Extension seam evidence (Stage 6)

- Upstream side (spike branch): `ToolProvider` interface (SAM; `toolClasses()` + default `domainCategories()`), `mcp-tools` Fabric entrypoint iterated in `ToolRegistration.buildRegistry` AFTER built-ins, through the same compatibility/access gating; provider domains registered before filtering, built-in domains non-overridable; 8 seam unit tests (provider contribution, duplicate skip, throwing/null providers, declared-domain registration, built-in-remap rejection, unknown-domain throw, clear hook).
- Our side (`src/mcpdev`, dev-only): `JujutsuModStatusToolProvider` (declares `jujutsu` → SERVER) + `JujutsuModStatusTool` (read-only, upstream `onMainThread` dispatch) + `JujutsuMcpdevBridge` (holds the server reference; upstream has no public accessor). Coupling direction mcpdev → {main, upstream}; **no production class imports MCP anything** (release jar audit proves it structurally).

## Release isolation evidence

- `mcpdev` source set is dormant without `-PmcpUpstreamJar` (all tasks SKIPPED; propertyless `assemble` green).
- Companion mods load only under `-PmcpSpike` (upstream jar via `modLocalRuntime` — Loom dev-remaps it; companion jar via `localRuntime` — already dev-mapped; both run-only configurations that never publish).
- `auditReleaseJarIsolation` extended: `com/chapmanjw/`, `io/modelcontextprotocol/`, `jujutsu/mcpdev/` prefixes + no `mcp-tools` entrypoint in the release descriptor. **Red-proven**: a fake `com/chapmanjw/Fake.class` in the jar → audit FAILED naming it → reverted → green (verbatim runs in `block-4-report.md`).
- Final on-content run: `qualityGate` exit 0 (metrics unchanged — mcpdev is outside every audit glob), `assemble` exit 0, jar = 1319 entries, 0 forbidden, descriptor clean.

## Platform findings

- **Windows (primary): everything above ran on Windows 11** — gradle builds, runClient with three mods, OMP sessions, port lifecycle. One quirk: upstream's wrapper pins Gradle 9.6.1 but 9.5.1 ran (machine-level; README floor 9.4; no effect).
- **Linux/Xvfb: not exercised** (allowed by the task when unavailable without distraction). The repo's existing `client-gametest.yml` proves the Xvfb rendering path for our own client lane; upstream CI builds its targets on Linux. Residual risk: none identified for the bridge itself beyond what upstream CI already covers.

## Corrected misconceptions from prior research

1. **No Yarn↔Mojmap gulf exists.** Both projects use Mojang official mappings; the "total rewrite" premise was wrong (issue #43 comment already said this; the spike confirms it).
2. **But "same mappings" ≠ "same names": Mojang renamed core classes between 1.21.8 and 1.21.11** (`ResourceLocation`→`Identifier` etc.). The prior scout claim "upstream already uses 1.21.8-era names, zero conditionals expected" was **false** — ~100 rename sites needed stonecutter conditionals. The port stayed mechanical; nobody's "2–4 weeks / fundamental gulf" estimate survived contact either.
3. **OMP is v17.2.5** (not "0.5.x" — a changelog misread during scouting). It still initializes with `2025-03-26` and accepts the upstream `2025-06-18` echo as-is.
4. **The single transport incompatibility was the one-shot GET SSE** — not protocol revisions, not sessions, not auth. Upstream's own docs promised the streaming behavior; the fix aligns code with its documentation.
5. **The real 1.21.8 blocker class nobody predicted: Minecraft's own parent-classloader Jackson 2.13** shadowing the bundled 2.22 — invisible on 1.21.11+ where the game ships a newer Jackson. Found only because the spike ran live tools.
6. **Upstream tool code is not MC-coupled** (13 of 144 files import `net.minecraft`); the port surface is the adapter layer, exactly as the layered architecture advertises.
7. **No dynamic tool-provider seam existed** (hardcoded 187-class list) and — new finding — the **closed category map is a second seam blocker**: an entrypoint alone is insufficient, the domain-category registry was also required.
8. **Stale OMP blockers are fixed upstream** (legacy SSE `7bab084`, stdio teardown `c30c3ab`) — neither affected this integration.
9. **The task's suggested decision-record path is unusable in this repo:** the removed research directory is gitignored and audit-forbidden (`tools/audit_docs.py` FORBIDDEN_REFERENCES), so this record lives at `docs/MCP_1_21_8_PORT_SPIKE.md`.

## Architecture decision

**A — port/fork upstream.** The spike proves the port is bounded and mechanical: transport, protocol, security, runtime dispatch, the full 193-tool surface, and the rendered-client endpoint all work on 1.21.8 after (a) one build target, (b) ~100 conditional rename sites + a dozen drift sites, (c) two genuinely new fixes (SSE hold-open, Jackson `fields()`), and (d) a ~60-line extension seam. Selected-module reuse (B) would discard a working 105-tool surface to save nothing — the expensive layers are exactly the ones that ported cleanly. A custom bridge (C) would mean owning protocol/transport/security/dispatch for less capability; the prior research's C recommendation is rejected on compile+runtime evidence.

**Fallback:** B (reuse protocol/transport/security/dispatch modules behind our own smaller tool surface) — only if upstream stewardship becomes untenable (e.g. the fork diverges unmaintainably when upstream moves further past 1.21.x). The seam and fixes are deliberately small and upstreamable to keep that risk low.

**Upstream contribution path:** the SSE hold-open, Jackson compatibility, and ToolProvider seam are all version-agnostic improvements upstream may want; proposing them as PRs would shrink our fork delta to just the 1.21.8 target.

## Next implementation slice (issue #43 L3 v0)

1. Fork hygiene: publish the spike fork (done as part of this PR's evidence), propose the three generic fixes upstream.
2. Grow `src/mcpdev` into the v0 surface from issue #43 (~12–18 tools): `vessel.select`, `action.invoke` (production router path), `fixture.reset`, cooldown/state queries — each through the same ToolProvider seam, read-only first.
3. Reproduce the PR #61 Todo aimed-swap GameTest interactively over MCP (the issue's "first proof of usefulness"), with `ticks.wait` semantics from upstream's async job pattern.
4. Wire a `-PmcpSpike`-style dev lane doc into `docs/BUILDING_IN_SANDBOX.md` and decide the fork's long-term home (repo org vs personal).

## Reproduction commands

```bash
# upstream scratch (fork): build the 1.21.8 target
./gradlew :1.21.8:build

# our worktree: companion jar
./gradlew.bat jarMcpdev -PmcpUpstreamJar=<abs path to minecraft-fabric-mcp-1.1.0+1.21.8.jar>

# live dev session (client + both endpoints)
./gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=<same path>

# endpoint configs (run/config/minecraft_fabric_mcp/): config.json (8765, auth_required + bearer_token), client.json (8766)
# OMP: .omp/mcp.json with type http, urls http://127.0.0.1:8765/mcp + 8766/mcp, Authorization header via ${MC_MCP_TOKEN}

# verification
./gradlew.bat qualityGate assemble
```
