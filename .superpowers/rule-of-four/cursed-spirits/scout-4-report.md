# Scout 4 report — Acceptance + Verification Map (verbatim)

# Cursed Spirits — Acceptance + Verification Map (scout #4)

## A. Requirements table (R1..Rn)

| ID | Requirement (imperative, observable) | Verification method | Expected observation |
|---|---|---|---|
| R1 | THREE entity tiers exist, are registered, and spawn in-game | GameTest + MCP (`entity_query`) | tier ids in `BuiltInRegistries.ENTITY_TYPE`; live query returns >=1 per tier after spawn |
| R2 | Each variant presents distinct model + texture + animation set | JUnit resource test + MCP `view_capture` | geo/anim/texture paths exist per variant; >=1 noon screenshot per variant shows different silhouette/texture (numeric bbox/region diff) |
| R3 | Tier-1 AI: wanders, chases, melee-attacks player | GameTest (AI mob, slowness-frozen) + MCP live | velocity/position delta toward target + hurt on victim within N ticks; MCP: `entity_get` HP delta |
| R4 | Tier-2 AI: R3 + one ranged or gap-close special | GameTest + MCP | special fires within timeout (projectile or teleport delta); cooldown row consumed |
| R5 | Tier-3 AI: R4 + area/phase mechanic, stays in arena | GameTest | mechanic oracle (effect tag / add / phase flag) fires; body inside structure bounds |
| R6 | Nobara nail melee damages every tier | GameTest (`hurtServer`, ACTIVE-phase premise) | body HP decreases by profile row; no exception |
| R7 | Hairpin (directed R + mass B) accepts cursed spirits as targets | GameTest + MCP (`jujutsu_ability_invoke`) | `routed:true` + cooldown delta + HP delta; mis-aim gives `routed:false`, zero cooldown |
| R8 | Resonance (incl. Self-Resonance) damages marked spirits | GameTest | marked spirit takes scaled damage; unmarked takes none |
| R9 | Todo melee + Black Flash damage spirits | GameTest | HP delta; Black Flash proc adds bonus + shared VFX cue |
| R10 | Megumi shikigami target and damage spirits | GameTest (`MegumiShikigamiTestFixtures`) | body count >0, spirit HP delta after sic within timeout |
| R11 | Normal damage path, no god-mode/immune flag | GameTest | no `Invulnerable` by default; two damage sources both reduce HP |
| R12 | Stagger via existing `CombatStagger` system on hit | GameTest + `jujutsu_state_get` | stagger entry present post-hit; clears after profile ticks |
| R13 | Knockback applies, scaled per tier (T3 resists most) | GameTest (AI mob + Slowness 100, NEVER `NoAI`) | velocity delta; T1>T2>T3 displacement ordering |
| R14 | Boogie Woogie ALLOW: T1/T2 swappable under STRICT placement | GameTest (`TodoSwapTestFixtures`, STRICT) | positions exchange; rotation/velocity preserved; cooldown starts |
| R15 | Boogie Woogie DENY: configured tier (default T3) refuses atomically | GameTest | `routed:false`, NOBODY moved, no cooldown |
| R16 | `TargetResolver` ranks spirits like any mob | JUnit + Nobara smoke | comparator tests green; centred far spirit beats near edge-graze (E1b smoke) |
| R17 | Spirit death drops CURSE remnant accepted by Resonance progression | GameTest | CURSE entry; progress counter increments; no orphan nails |
| R18 | Spawn weights satisfy T1>T2>T3 over fixed-tick window | GameTest (seeded RNG) + JUnit | Count(T1)>Count(T2)>Count(T3); test pins literal numbers |
| R19 | Caps enforced: per-player/chunk cap + despawn/expiry, no flood | GameTest + MCP soak | spawn past cap refused/recycled; census stable after 1200-tick soak |
| R20 | Balance centralized in ONE editable profile | JUnit + inspection | one profile class; no duplicated literals; red-proof (mutate row -> fail) |
| R21 | Animations idle+walk+attack+hurt+death play per variant | MCP frozen-frame pairs + JUnit clip test | slowness-8 frozen vs walking diff; death removes body + cue; policy pins clip names |
| R22 | Correct texture per variant, no missing-texture magenta | MCP screenshot + JUnit path test | texture files exist; noon close-up mean-color differs per variant |
| R23 | Sounds ambient/hurt/death fire (placeholders allowed, recorded) | GameTest hook + manual listen | event counter increments; manual: coherent, non-silent |
| R24 | Hitbox not broken: hittable by ray, size ~= render | GameTest + MCP aim | ray-clip hits AABB; screenshot bbox vs AABB within tolerance |
| R25 | Killable: every tier dies, removal flushes lookup | GameTest (`removeAndVerifyGone`) | `isRemoved=true`, absent 10 ticks later |
| R26 | Multi-variant simultaneity: >=3 variants share one tier mechanic | MCP live | one frame + query shows 3 distinct looks; mechanic oracle true for all |
| R27 | Automated tests ship: JUnit + GameTest | gate inspection | `runGameTest` + `test` cover all; each red-proven |
| R28 | Docs/provenance for Sons-of-Sins reuse, no silent expansion | `auditDocumentation` + inspection | PROVENANCE + THIRD_PARTY entries; MOC metrics + index updated |
| R29 | Live MCP visual+behavioral pass per tier | MCP lane chain (B4) | checklist signed per tier with frames + deltas under `.omp/smoke-run-<n>/` |
| R30 | Perf sanity: 10+ spirits keeps TPS~20, no spam/OOM | MCP soak (`server_get_status`) | TPS>=18, MSPT stable, no repeating ERROR |

## B. Infrastructure map

### B1. qualityGate (build.gradle) — `gradlew.bat qualityGate --no-daemon --max-workers=1 --no-watch-fs`
- Deps: `check` (compileJava+clientJava, `test` JUnit, ALL `group=verification` JavaExec, `runGameTest` via Loom) + `auditDocumentation` (python audit_docs.py) + `verifyAssertionsEnabled` (`-ea` audit) + `auditReleaseJarIsolation` (remapJar scan). Runtime ~50-120 s warm (52 s observed). Green gate proves shape/contracts/pure logic ONLY, never in-world behaviour (KNOWN_ISSUES Limits+E1).
- Pins (gradle.properties): Java 21 (`options.release=21`), MC 1.21.8, loader 0.19.3, loom 1.17.17, fabric-api 0.136.1+1.21.8, geckolib 5.2.2. JAVA_HOME = Temurin 21 (`BUILDING_IN_SANDBOX.md`, checksum tarball); `jvmargs=-Xmx1G`, `configuration-cache=false`.
- Focused: `gradlew.bat test --tests "jujutsu.mod.combat.TargetResolverTest" --no-daemon`; `gradlew.bat runGameTest|runClientGameTest|auditDocumentation|verifyAssertionsEnabled --no-daemon`.

### B2. GameTest lane
- Scenarios `src/gametest/java/jujutsu/mod/gametest/`: `ServerGameTests` (2 canaries), `TodoAimedSwap(GameTests|Rollback)`, `TodoStoneGameTests`, `NobaraAbilityResultGameTests`, `MegumiShikigami(GameTests|Toad|Rabbits|Elephant|CrossTests)`. Fixtures: `GameTestFixtures` (`spawnWithNoFreeWill`, removeAndVerifyGone, diagnostic), `TodoSwapTestFixtures`/`MegumiShikigamiTestFixtures` (mock player, select vessel, clear cooldowns AFTER select, static cleanup on success+failure).
- Register: append class to `fabric-gametest` array in `src/gametest/resources/fabric.mod.json`. Run-only: `gradlew.bat runGameTest --no-daemon --max-workers=1 --no-watch-fs`. Report `build/test-results/gametest/junit.xml` (parse XML, NOT log); logs `build/run/gameTest/`.
- Arena traps (gametest-authoring SKILL.md): (1) barrier walls at rel-8 + floor rel y=-1 — throw UP or <=6 blocks; (2) world offset random +-7-12M, sign flips — RELATIVE asserts only; (3) mock `kill()` no-ops — use `die(genericKill())`, `hurtServer` gated on ACTIVE; (4) `NoAI:1b` freezes position — displacement needs AI+Slowness-100; water drag 0.8/tick. Red-proof duty per scenario.
- Client lane (NOT in gate): `gradlew.bat runClientGameTest --no-daemon`; canaries `ClientLoadCanaryTest`, `ClientObservationCanaryTest` (FLAT/seed-1/noon-6000/NoAI-pig/854x480 `0000_observation_canary.png`), `SdfGlassCanaryTest`; artifacts `build/run/clientGameTest/`; result = exit code only.

### B3. JUnit lane
- `src/test/java/jujutsu/mod/` on JUnit5 + fabric-loader-junit (`Bootstrap.bootStrap()` in `@BeforeAll`, `failOnNoDiscoveredTests=true`). Examples: `combat/TargetResolverTest`, `combat/CombatStaggerClearTest`, `curse/CurseLinkRegistryTest`, `character/megumi/*Policy|*Brain`, `character/nobara/projectjjk/{HairpinChain,NailTrap,ResonantMomentum,RemnantVisualType}Test`, `client/render/megumi/*ResourcesTest`, `architecture/*Test`. Single class: `gradlew.bat test --tests "jujutsu.mod.curse.CurseLinkRegistryTest" --no-daemon`. Legacy JavaExec `main()+assert` (e.g. `testTargetResolver`) run via `check`; 4 mutable-static owners move LAST.

### B4. MCP dev lane
- Launch: `gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/minecraft-fabric-mcp-1.1.0+1.21.8.jar --no-daemon --max-workers=1 --no-watch-fs --console=plain` (+`JAVA_TOOL_OPTIONS=-Xmx3G`); `prepareMcpSpikeRun` seeds `run/saves/mcp-spike` + options. Readiness: BOTH `MCP server listening at http://127.0.0.1:8765` and `8766`; `tools/list` on 8765 = ground truth (115 total: ~104 upstream + 11 jujutsu). Token `run/config/minecraft_fabric_mcp/config.json` -> `MC_MCP_TOKEN` (`~/.omp/agent/mcp.json`). Stop process after; never leave running.
- jujutsu_* (11, `JujutsuModStatusToolProvider.toolClasses()` + code schemas): `jujutsu_vessel_list` (-); `jujutsu_vessel_select` req(player_uuid, vessel_id); `jujutsu_ability_invoke` req(player_uuid, slot)+opt(expect_vessel, notify); `jujutsu_cooldowns_get` req(player_uuid); `jujutsu_cooldowns_clear` req(player_uuid)+opt(slot); `jujutsu_fixture_reset` req(player_uuid); `jujutsu_fixture_list` (-); `jujutsu_state_get` req(player_uuid); `jujutsu_ticks_wait` req(ticks 1-1200)+opt(player_uuid); `jujutsu_player_set_rotation` req(player_uuid, yaw, pitch); `jujutsu_mod_status` (-). New class MUST enter `toolClasses()`.
- Upstream (names verified from spike evidence + skills; schemas in upstream jar, confirm via `tools/list`): `server_get_status`, `level_get_dimension_info`, `level_get_time`, `entity_summon` (position obj), `entity_get` (uuid), `entity_query` (selector `@a`), `entity_despawn`/`entity_kill` (uuid; selectors via `command_execute /kill @e[...]`), `entity_teleport` (position+facing objs; facing moves camera AND server rotation), `entity_apply_effect` (uuid ONLY, no dimension), `command_execute` (`time set noon`, `kill @e[type=minecraft:slime,distance=..64]`), `client_status`, `sense_entities|_crosshair|_raycast|_screen` (crosshair/raycast MISS entities — use HP/cooldown oracles), `view_capture` (854x480 PNG, ~0.3 s/shot).
- Mob chain: `entity_query @a`->uuid -> `command_execute time set noon` + clear slimes -> `entity_summon` at known pos -> `entity_teleport` camera to face (pitch +15..+40) -> `jujutsu_ticks_wait` -> `view_capture` -> `jujutsu_ability_invoke` -> `entity_get` HP delta + `jujutsu_state_get`/`cooldowns_get` delta -> `entity_apply_effect slowness amp 8` for stills -> `fixture_reset`. Sounds: NO direct oracle — infer via success + logs. Frames judged NUMERICALLY (luminance, bbox, pixel mask; vision unreliable <150 px).

### B5. Docs audit (tools/audit_docs.py as `auditDocumentation`)
- Enforces: 9 CURRENT_DOCS exist; 8 REMOVED_DOC_DIRS untracked; no FORBIDDEN_REFERENCES; no HISTORICAL marker; relative links resolve; 6 MOC metric tokens exact (Main|Client|Test Java files, Verification programs = `tasks.register('testX', JavaExec)` count, Client mixins, Nobara VFX ids). Fail = gate red.
- New system MUST: extend PROVENANCE.md (source commit/blob-SHA, shipped vs unshipped, permission class) + THIRD_PARTY_NOTICES.md; Codex note `03-systems/<System>.md` + MOC link + metric bump; accepted limits in KNOWN_ISSUES.md; SESSION.md handoff; keep `docs/README.md` list synced; lang parity by hand (no gate, E5).

## C. Cannot be automated
- Tier look/feel + variant distinctness ("different spirits, one mechanic") — side-by-side noon screenshots, tight crops, numeric diff as support, owner eyeball verdict.
- Animation timing/weight (wind-up, hit beat, death readability) — burst `view_capture` + motion mask; feel judged live.
- Sound coherence (voice fits body, mix, no stacking/leak) — play session per tier x hurt/death; no MCP audio oracle.
- Spawn distribution in a REAL world (biome/light/pack feel, cap comfort) — 10+ min soak + TPS/MSPT; seeding cannot reproduce perception.
- Hit feedback feel (knockback, stagger duration, T3 menace) — hands-on combat per vessel; asserts pin numbers, play judges tuning.
- Crowd readability (5+ bodies, target-priority confusion E1b) — spawn several variants, screenshot + fight live.

Out-of-scope
- New vessel kits, CE bars, crafting/progression, public-server balance (hit-stop global + no-occupancy gates stay).
- Upstream MCP fork changes, new MCP tools beyond the 11, multiplayer/PvP matrix.
- Asset re-licensing beyond recording scope (R1-R3 stay open); renderer refactors (E6/E14) unless forced.
- Golden-image/SSIM gating and audio-capture oracles (explicit non-goals).
