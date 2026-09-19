# Session Handoff — integration branch `integration/megumi-incidents-107-110` — 2026-09-19

## State — Phase 3 review wave done, fixes applied; qualityGate + in-game pending

The integration branch merges PRs #115 (selector, #109), #116 (cursed incidents, #110),
#117 (coexistence/partials, #107/#108) plus a full fix wave. Phase 3 review wave
(4 reviewers + QA) landed ~20 fixes: durable dirty marking, seal decay anchor policy,
container setChanged, per-dimension clocks, secondary work-center edits, cap
re-registration, disconnect cleanups, orphan node spirits, doc corrections.

Remaining: commit review fixes, full qualityGate, in-game MCP verification per
`.superpowers/rule-of-four/integration-107-110/ingame-checklist.md`, open the
integration PR (RU title, «Для игрока», supersedes #115/#116/#117, refs #107-#110).

## Previous session (domain-sphere PoC — MERGED 2026-09-16)


---

# Session Handoff — Megumi shikigami quick selector (issue #109) — 2026-09-17

## State — implemented, verified in game, at branch

Branch `feat/megumi-shikigami-selector` in worktree `D:/WorkFlow/jujutsu-selector-wt` (main
checkout `D:/WorkFlow/jujutsu-minecraft` belongs to another agent — never touch it). Commits:
B1 `26f1753` … B5 `713015a`, review-fix commit `c68aa0c`. `qualityGate` green (160 GameTest,
624 JUnit, doc audit, jar isolation). **Not pushed, no PR — waiting for the user's order.**

Feature: hold G opens a bottom-center strip of five shikigami slots (no pause); click selects,
cooling slots reject, summoned shows a marker; tap G cycles like `S+V`. Wire: S2C snapshot +
C2S select. New mcpdev tool `jujutsu_input` injects tap/press/release/hold/click — the upstream
client tools are read-only, so the gesture is now scriptable end to end.

In-game verified on lane `selector-lane` (ports 8775/8776): tap cycles, hold opens/closes,
click selects ready and rejects cooling, SUMMONED marker shows, world ticks while open, no crash.

## Traps learned this session

- **PiP render crash**: `submitEntityRenderState` (picture-in-picture) bypasses the GeckoLib
  dispatcher mixin — inject `PACKED_LIGHT` into the preview render state or it NPEs on hold.
- **`KeyMapping.set`/`click` via `InputConstants.getKey(saveString)` silently no-ops** (the
  key map misses); only instance `setDown` works — `jujutsu_input` drives that on the client
  thread via `Minecraft.execute` + an `END_CLIENT_TICK` re-assert (survives `setAll`).
- Fast hold-release can strand the strip: the screen's watchdog must latch
  `selectorKeyWasDown` in `init()` from `selectorGesture.isOpen()`, not from the physical key.
- GameTest flake `megumi_toad_game_tests_already_held_victim_refuses_second_holder` fired once
  (cooldown oracle), re-ran green — same family as issue #104.

## If work continues here

1. Push + PR (Russian title, «Для игрока» body per §12) — only on the user's order.
2. Manual-only leftovers: sound check on real ears (open/hover/select/reject), GUI-scale edge
   cases, rebinding the key off G (watchdog fallback is hardcoded to KEY_G — QA P3-3).
3. Pipeline artifacts: `.superpowers/rule-of-four/megumi-selector/` (plan, scout/review/QA
   reports, progress.md).

---

# Session Handoff — domain-sphere SDF PoC — MERGED 2026-09-16

## State — DONE: PR #112 merged to main (merge commit bc71af4, branch feat/domain-sphere-vfx). Ring fix 190e467 is INSIDE the merge — final shader = unclamped first-PR brightness + fade-sync kept. main == origin/main, tree clean.

Branch `feat/domain-sphere-vfx` (pushed? — check `git status`/`git log origin/` before assuming).
Goal: first world-space SDF sphere for the future Domain Expansion — ONE large cyan/neon spherical
shell expanding ~0 → configurable radius around a fixed world point, depth-aware fullscreen
raymarch post-effect. Orbital Railgun `strike.fsh` central-sphere technique ported to the project's
own MC 1.21.8 stack. **No Satin, no beams/outer-spheres/columns/explosion/chromatic aberration, no
gameplay mechanics, no VFX-pipeline refactor.** Separate PR at the end (Russian title, «Для игрока»
body per AGENTS §12).

## Pipeline state (rule-of-four)

Work dir: `.superpowers/rule-of-four/domain-sphere/` (gitignored; write via `exec`, the write-tool
may refuse gitignored paths — `edit` on existing files works fine).

- **Phase 0 DONE** — 4 scouts ran; only `scout-2-report.md` survived on disk (the rest lived in
  session context — key facts are baked into the plan's "Verified API facts" section). Ref clone:
  `ref/orbital-railgun` (MC 1.20.1 + Satin — port is NOT mechanical).
- **Phase 1 DONE** — `implementation-plan.md` (257 lines, 5 tasks, R1–R21 traceability, pinned
  contracts between workers).
- **Phase 1.5 DONE** — 3 independent reviews: buildability=correct (all API claims javap-verified,
  no P0/P1), architecture=correct-after-fixes (P1: lifecycle ownership inverted — FIXED in plan),
  acceptance=incorrect-as-written (R12 reload/resize unexercised, easing shape testable-on-linear —
  FIXED in plan). Dispositions in `plan-review.md`.
- **ALL PHASES DONE** — Phase 2 workers landed, barrier green (qualityGate 310/0), Phase 3 review wave + QA done, Phase 4 judge VERIFIED + in-game done, user-feedback fixes 2bb3d6e + 190e467 committed and merged.
- **Remaining: manual-only checks for the user** — F3+T mid-effect, rim quality by eye, world change/disconnect mid-effect (synthetic input never reaches the game window).

## Key decisions (do not re-derive)

- New `VfxDomainSphereChannel` (state) + `DomainSphereRenderer` (GPU: color+depth scene copies,
  fullscreen `RenderPass`, 160B std140 `SphereData` UBO, ≤4 spheres one draw each). Hook:
  `WorldRenderEvents.LAST` via `VfxDirector::renderLast`. Lifecycle registration lives in
  `VfxDirector.initialize()` — channel ctor registers NOTHING and does NO GL (JUnit-safe).
- Effect id `jujutsumod:domain_sphere` in `src/main` `DebugVfxIds` — referenced ONLY from
  client/mcpdev code (else `VfxCompletenessTest.compiledProductionEmittersCoverEveryLiveId` reds).
  Debug recipe registers outside the four vessel packs (the test counts exactly 69 ids).
- Camera-relative math: center passed as `centerWorld - camPos`; shader rebuilds camera-relative
  world pos via `InvProjMat`/`InvViewMat` — fp32 precision at large coords. Vertex shader does NOT
  Y-flip texCoord (blur.vsh's flip is GUI-only).
- Depth: copy main target depth into a DEPTH32 texture, sample `.r` — vanilla
  `RenderTarget.copyDepthFrom` proves `copyTextureToTexture` works on DEPTH32. First frame logs a
  distinct `[DomainSphere] depth copy OK/FAILED` marker. Fallback = bind `getDepthTextureView()`
  directly (documented, changes occlusion semantics — last resort).
- Timing in Java (`DomainSphereTiming`: easeOutQuart expand 20t / hold 220t / fade 20t); shader
  gets final radius/fade/progress. Tests must catch a linear ramp (`radiusAt(half) ≥ 0.9·max`).
- Triggers: client command `/jujutsu_debug domain_sphere [radius]` + mcpdev `jujutsu_domain_sphere` tool
  (routes through `JujutsuNetworking.sendVfxCue` — server authority preserved).
- Known accepted limit: Fabulous-mode translucents never write main depth → sphere occludes only
  vs opaque geometry (same as the reference).

## Environment notes

- Java 21 required: `JAVA_HOME=/c/Users/KOMP1/scoop/apps/temurin21-jdk/current` (PATH java is 1.8,
  JAVA_HOME was 17 — Gradle toolchain resolves 21 when pointed right).
- `./gradlew qualityGate` is the finishing gate; JUnit-only is a mid-work check.
- In-game verification via MCP dev lane — see `docs/MCP-LANE.md` + `.claude/skills/mcp-lane-launch`.
- Untracked scratch in worktree (`audit/`, `WATCHDOG.yml`, `nul`) — deliberately not committed.

## Next-session prompt

Domain-sphere is fully closed — no continuation prompt needed. Open backlog: #104 (GameTest flake family), #22 (shared static runtime state), #83 (cursed tools, needs DESIGN SPEC), upstream MCP fork issue #1.

---

# Session Handoff — review campaign + MCP dev-tools — 2026-09-15

## State — all merged, live-verified

Full post-release review of the Cursed Saga / shikigami slices (PRs #74, #75, #87, #89) ran through
a subagent fleet; findings became issues **#90–#99**, fixed by four goal-worker blocks and merged as
PRs **#100–#103**. Follow-up tooling PR **#105** added eight `jujutsu_*` dev-control tools. `main` @
`61090fc`, `qualityGate` green — **155 GameTests**, JUnit, doc audit, jar isolation.

- Merge order and SHAs: #101 abilities/spawn `b1b53aa` → #102 perception `12a3445` (plus seam commit
  `0bbe8ed` wiring `absorb(…, source)` + `loadFrom(input, grade)` — the BYPASSES_ARMOR filter and
  grade-gate are live now) → #103 hold `5e5037e` → #100 megumi `c26fbf1` (carries de-flake `662956c`:
  the slam-crater oracle is spirit-relative now, not absolute-arena).
- Issues: **#91–#99** closed via `Closes`; **#90** closed manually after the owner resolved point 8 —
  "no interaction between a non-mage and a curse" is the design, and issue **#80**'s acceptance text
  was corrected to match. **#104** opened for the episodic GameTest flake family (toad-grab
  deadlines, combatSilencesShelter).
- In-game verification on the dev lane (mod 1.7.5 per `jujutsu_mod_status`): the perception contract
  holds both directions — a non-mage's client never receives the spirit at all (server tracking
  filter, not just render), no aggro, no damage; megumi sees it, gets acid-spit and meleed, and
  switching back to `none` mid-combat de-aggros and un-tracks it live. Fear lands
  (`cursed_fear`+darkness+nausea markers), slam launches the player airborne, toad grip pins the
  victim with `gripped`+slowness and releases clean, elephant presence steadily drains a marked
  aggressor (#91 fix confirmed live), rabbit swarm summons 10 bodies, `difficulty peaceful` despawns
  every spirit.
- mcpdev tools (#105): `jujutsu_entity_attack` (source-aware damage — the non-mage↔spirit damage
  gate is verifiable now), `jujutsu_combat_log` (damage/death ring buffer with projectile-owner
  attribution), `jujutsu_wait_until` (condition waits, any/all), `jujutsu_player_respawn`,
  `jujutsu_player_set_gamemode`, `jujutsu_entity_set_health`, `jujutsu_look_at`,
  `jujutsu_entity_summon_near`. Lane ops guide: `docs/MCP-LANE.md`; smoke evidence:
  `audit/mcpdev-tools.md`.
- The generic MCP layer is a separate codebase — the fork
  `grebeshok105/minecraft-java-fabric-mcp-server` (local checkout
  `D:/WorkFlow/mcp-spike-scratch/upstream`, branch `mc-1.21.8-target`, built jar wired via
  `-PmcpUpstreamJar`). Follow-ups that can't be done in mcpdev are filed as issue **#1** there
  (deliverable entity.damage/death events, soft-miss entity_get, command_execute output, client-side
  sense_sounds/rendered_entities, relative positions, second headless client for MP checks).

## Traps learned (full versions in `docs/MCP-LANE.md`)

- The session MCP bridge can die while the game-side HTTP server stays up — bypass with
  `.agent-runs/ingame/mc_call.py` (JSON-RPC `tools/call`/`tools/list` on `127.0.0.1:8765` world /
  `8766` client-sense).
- Killing the gradle shell does NOT kill the game window: find the java PID (`jps -lv`),
  `taskkill //PID //F`, confirm port 8765 is free, then relaunch. One live client per lane, always.
- `command_execute` drops text output (`data get`/`gamemode` report `successCount 0` on success);
  `entity_get` throws "Entity not found" on dead bodies (use `jujutsu_wait_until entity_gone`);
  `distance=..N` measures from the command-source origin, not the player; tag selectors
  `@e[type=#…]` return `[]`.
- NBT summon recipes that work: ability pool `{Abilities:"fear,dash,armor"}` (flat keys on the
  entity), health buff `{attributes:[{id:"minecraft:max_health",base:200.0}],Health:200.0f}` —
  the camelCase `Attributes` form silently does not apply.

## If work continues here

1. The fix batch shipped as **v1.7.6 beta hotfix** (2026-09-15): `chore(release): 1.7.6` = `56679ce`,
   tag `v1.7.6`, GitHub prerelease carries `jujutsumod-1.7.6.jar`; deployed to
   `D:/Games/instances/Jujutsu/mods/` (1.7.5 → `.bak-20260915`); issues #90–#99 carry release
   comments. Lane re-verified live: `jujutsu_mod_status` reports `mod_version: 1.7.6`.
2. **#104** is the standing instability item (episodic GameTest flakes); first suspect is the toad
   self-pick cooldown window vs the sic deadline.
3. **#22** (shared static runtime state) stays open as systemic debt — the concrete ZONES/CARRIED
   holes are patched, the architecture point isn't.
4. Upstream fork issue #1 is the shopping list for the generic MCP layer; implementing it means a
   PR on the fork, a jar rebuild, and re-pointing `-PmcpUpstreamJar`.
5. The lane player is mortal and the dev world is hostile — `jujutsu_player_respawn` +
   `jujutsu_entity_set_health` exist now; `doImmediateRespawn` in the mcp-spike save is intentionally
   left `false` (death scenarios need it).
6. `audit/` and `WATCHDOG.yml` are deliberately untracked session-local scratch (`20fe6b3`) — do not
   commit them.

---

# Session Handoff — released as `v1.7.5` beta (2026-09-13)

## State — RELEASED

The Cursed Saga epic shipped: PR **#89** (`feat/cursed-saga`, 6 commits) merged as `e772a7d`,
`chore(release): 1.7.5` = `b18363f` on `main`, tag `v1.7.5`, GitHub release (prerelease/beta) carries
`jujutsumod-1.7.5.jar` (md5 `1803f152f1f2e6bcf06be00060d5f966`). Release notes are player-facing
Russian per §13; the technical part lives in the PR body.

- Idea issues **#79, #80, #81, #82, #86** closed with release comments — all five were implemented
  (toad grab via shared `HoldSupport` pin + elephant presence/footprint; mage-only perception;
  grade 5/4/3 axis with discrete bands; day spawn schedule + shelter; 8-ability pool, 3 per spirit).
  **#83** (cursed tools) stays parked — it was never in this epic's scope.
- Game instance `D:/Games/instances/Jujutsu/mods/` holds exactly one live mod jar, `jujutsumod-1.7.5.jar`
  (md5 matches the build); `1.7.0` moved to `jujutsumod-1.7.0.jar.bak-20260913`.
- Verification at release: `qualityGate` green — **142/142 GameTests, 541 JUnit**, doc audit + jar
  isolation green; live MCP smoke **16/17** (elephant summon→pack→ACTIVE→presence pulse→jet, toad
  summon→grab pin drift 0.000; the 17th was a driver-timing artifact, not a mod bug). Mutation
  red-proof on Block 5 (HOLD_MIN mutation reds the suite, revert re-greens).
- Post-release de-flake (`3a66419`, on main above the release commit): two intermittent CI reds
  root-caused to test oracles, not product — `combatSilencesShelter` now seeds the target directly
  (vanilla `NearestAttackableTargetGoal` random gate, ~1/13 000 tail) and the slam outsider moved
  from (6,1,6) to (1,1,6) (body-centred blast drifts ~1 block past the crater; grade-3 hitRadius 4.3
  vs the old 4.47→4.1 clearance). Verified by 5 consecutive green lane runs + green CI.

## If work continues here

1. Independent reviewer pass never ran (provider 429, ~6 h window) — re-run before relying on the
   branch for the next slice; PR #89 body carries the full technical summary.
2. `docs/KNOWN_ISSUES.md` #9: dev-lane death trap — persisted spirits near spawn can kill a fresh
   lane player and pause the integrated server; offline save cleanup is the recovery path.
3. Pipeline artifacts: `.superpowers/rule-of-four/cursed-saga/` — `implementation-plan.md` (rev 2.4+),
   `plan-review.md`, `scout-1..4-report.md`, `block-5-report.md`, `progress.md`, `analysis/` live scripts.
4. Next idea candidates: #83 (cursed tools) needs a DESIGN SPEC first, per the brainstorm gate.

# Session Handoff — released as `v1.7.0` beta (2026-09-12)

## State — RELEASED

The post-1.6.5 bugfix slice shipped: PR **#87** squash-merged as `8bbc582`, the stronger punishment
oracle as PR **#88** (`d03960f`), then `chore(release): 1.7.0` = `bc370ff` on `main` (the first cut carried the two-part `1.7`; three-part is the repo convention, and the jar name outlives the mistake, so the tag was re-cut before anyone could depend on it).

- GitHub release `v1.7.0` (prerelease/beta) carries `jujutsumod-1.7.0.jar` — 17 741 018 bytes,
  md5 `8359d6b553ae9c29670f64fab5ef5dbd`, body byte-identical to `.superpowers/rule-of-four/bugfix-76-85/release-1.7.0.md`
  (4431 bytes, verified after publishing — the check that caught a mangled body last release).
- Game instance `D:/Games/instances/Jujutsu/mods/` now holds exactly one mod jar, `jujutsumod-1.7.0.jar`
  (md5 matches the build); `1.6.5` moved to `jujutsumod-1.6.5.jar.bak-20260912b`.
- Issues **#76, #77, #78, #84, #85** are closed with a comment pointing at the release. Only the idea
  issues stay open (#79–#83, #86 — all `BRAINSTORM ONLY:`, they need design specs before work).

## What 1.7.0 fixes

Five reported bugs (#77 animation arbitration, #85 whiff slam, #78 rabbit locomotion, #84 cooldown
reset on a vessel switch, #76 pack retaliation) plus three defects found by re-reading the retaliation
work: the freshness window compared the entity clock against the level clock, the per-tick re-mark
cancelled every pounce, and `OwnerHurtTargetGoal` stole a manual sic. Every behaviour carries a
GameTest with its own mutation proof; suite is **97 GameTest / 0** and **435 JUnit / 0**, `qualityGate`
green, jar isolation 1502 entries.

## If work continues here

1. Owner smoke-tests 1.7.0 in their own instance (the jar is deployed; a full game restart is required).
2. Next slice candidates are the idea issues: #79 (Elephant/Frog gameplay), #80 (spirits only visible
   to sorcerers), #81 (level 3–5 + stat spread), #82 (daytime spawning), #86 (per-spirit ability pools),
   #83 (cursed tools) — each needs a DESIGN SPEC first, per the vessel/systemic rules.
3. Live-lane traps learned this session are in `.claude/skills/mcp-lane-launch/SKILL.md` (JAVA_HOME for
   a supervised launch, one MCP session per process, base64 `view_capture`, the mortal lane player and
   `doImmediateRespawn`).

# Session Handoff — post-1.6.5 bugfix slice (#76/#77/#78/#84/#85) — 2026-09-12

## State — fixes done, gate green, live-verified, at PR

Branch `fix/cursed-spirits-feedback`, five commits on top of `a3343c1` (the issue-filing commit):

| commit | issue | fix |
|---|---|---|
| `4012f67` | #77 | `CursedSpiritClips` arbitrates the layers once per frame (scream > attack > idle+walk) instead of letting nine models stack them; `beginScreamAnim` extends an already-running scream instead of restarting the hurt window |
| `a165e68` | #85 | the greater slam fires even when the tagged victim leaves reach — only the direct hit keeps the reach rule |
| `2dc38cd` | #78 | `MegumiRabbitSwarmPolicy` + `RabbitChaseGoal`/`RabbitDriftGoal`: the swarm has a locomotion goal again (ring drift around the owner, chase only inside the band) |
| `cced503` | #76 | `MegumiRetaliationPolicy` + per-owner pass in both runtimes: the pack answers a `lastHurtByMob` within 100 ticks or a mob whose target is the owner within 16 blocks; `sicManual` keeps an explicit sic on top; vanilla `OwnerHurtByTargetGoal` removed from all five bodies (it stole manual sics); `Facts.ownSummonBody` stops friendly fire |
| `b1905e5` | #84 | `CharacterAbilityCooldowns.clearForCharacter` on a vessel switch (server + client mirror); re-confirming the SAME vessel clears nothing on purpose |
| `f0b1051` | #76 (follow-up) | three defects found by re-reading the first pass: the retaliation window compared the entity clock against the level clock (vanilla stamps `lastHurtByMobTimestamp` with `tickCount`), the per-tick re-mark cancelled every pounce, and `OwnerHurtTargetGoal` stole a manual sic. Each has its own GameTest + mutation proof |

The pipeline lives in `.superpowers/rule-of-four/bugfix-76-85/` (`implementation-plan.md`, `progress.md`,
`scout-1..4-report.md`, `analysis/{live_fix_check.py,mc_mcp.py,shot.py}`, frame evidence).

- Barrier: `qualityGate` **BUILD SUCCESSFUL** — **97 GameTest / 0** (was 88), **435 JUnit / 0** (was 421),
  doc audit + jar isolation green. Every new behaviour carries a red-proof (mutation → red → restore):
  clip arbitration, whiff slam, rabbit displacement, retaliation (R1/R2 red, R3 stays green), vessel-switch reset.
- Live MCP pass on the dev lane (`analysis/live_fix_check.py`, one session — a fresh MCP session per call
  trips the lane's 429): #84 `PRIMARY 231 → 0` across a switch and `0` after switching back; #78 ten bodies,
  all moving, median **2.80 blocks** with the owner parked; #85 player **20 → 12 HP** beside a `walking_bed`;
  #76 a 12-HP zombie brought **12 → 9.2 → 0.38 → dead** in ~60 ticks by the pack with both dogs alive and the owner untouched (the earlier 'one bite then silence' was the pack being killed: `Divine Dog was slain by Husk`); #77 five frames
  with coherent geometry and a clean client log.
- Trap for the next live pass: the lane player is mortal — a `walking_bed` killed them mid-session, and a dead
  player breaks every later probe silently (`entity_get` → "Entity not found"). Respawn via `computer` →
  window "Minecraft* 1.21.8" → vision model locates the button → click; then keep `resistance` on.

## What is where

- `src/client/java/jujutsu/mod/client/render/cursedspirit/CursedSpiritClips.java` — the single clip arbiter (#77).
- `src/main/java/jujutsu/mod/character/MegumiRabbitSwarmPolicy.java`, `MegumiRetaliationPolicy.java` — pure policies.
- `src/main/java/jujutsu/mod/character/CharacterAbilityCooldowns.clearForCharacter` + `CharacterSelectionManager.select` — #84.
- `docs/KNOWN_ISSUES.md` **E1a** was rewritten: it recorded the old "cooldown survives a switch" policy as intended.

## If work continues here

1. Merge this PR, then cut the release (`mod_version` +0.1 — it is a bugfix batch) and ship the RU player-facing notes.
2. The idea issues are NOT in this slice and still need design specs: #79 (Elephant/Frog kit), #80 (spirits only
   visible to sorcerers), #81 (level 3–5 + stat spread), #82 (daytime spawning), #86 (ability pools), #83 (cursed tools).
3. Re-run the manual smoke on the merged build: the owner plays #75's acceptance list; the client must be restarted
   so the new jar is loaded.

# Session Handoff — cursed spirits (three hostile tiers) — 2026-09-12

## State — RELEASED as `v1.6.5` beta

Owner feedback from the first play session after 1.6.5 was filed as issues **#76–#86** on 2026-09-12:
bugs — #77 (cursed-spirit jitter and the model sliding off on hit), #78 (Rabbit Escape stands in place),
#84 (cooldowns survive a vessel switch), #85 (Walking Bed attacks deal nothing), #76 (shikigami should
retaliate on their own); ideas — #79 (fill out Elephant/Frog), #80 (spirits visible only to sorcerers),
#81 (random spirit level 3–5 + stat spread), #82 (daytime spawning), #83 (BRAINSTORM: cursed tools),
#86 (per-spirit ability pools and tier variation). Bodies carry file-level starting points and acceptance
criteria, so they are ready to pick up as the next slice.

PR #75 squash-merged into `main` as `3a0936c`; the release commit bumps `mod_version` to **1.6.5**
and the GitHub release carries `jujutsumod-1.6.5.jar` (beta). The feature branch stays on the
remote on purpose, as with the shikigami slice: the squash collapses its commits.

Three hostile cursed-spirit **tiers** (`lesser_cursed_spirit` / `cursed_spirit` /
`greater_cursed_spirit`) with nine visual **variants** imported from the Sons of Sins pack, natural
overworld spawning, and the full combat-core seam set (stagger floor, Boogie Woogie immunity for
greater bodies, nail marks + remnant minting, Megumi sic targeting). Gameplay belongs to the tier,
look belongs to the variant: one entity class, one profile, one variant enum.

- Barrier: `qualityGate` **BUILD SUCCESSFUL** — **88 GameTest cases / 0 failures** (was 67 before this
  slice), **421 JUnit / 0**, doc audit + jar isolation + assertion checks green (log:
  `.superpowers/rule-of-four/cursed-spirits/gate-log.txt`).
- Review wave: four reviewers (assets, server core, client stack, spawn+integration). Verdicts GO for
  the first three; the fourth's NO-GO items (spawn-GameTest fratricide, missing R7/R8/R16 scenarios,
  weak oracles) were all fixed and re-proved. Adjudication + resolutions:
  `.superpowers/rule-of-four/cursed-spirits/{review-spec.md, judge-followups.md}`.
- Independent QA: 420 JUnit + 88 GameTest green, docs audit green, provenance hashes verified 5/5,
  every accepted limit cross-checked against the code; judge: all load-bearing claims VERIFIED.
  The five never-reddened integration scenarios were reddened by one batched mutation lane (5 mutations,
  7 lane reds + 2 JUnit reds, then restored green) — evidence in `progress.md`.
- **P0 found by the live MCP pass and fixed**: `ATTACK_END` was byte id 63, and the vanilla client
  listener casts id 63 to `Sniffer` unconditionally — so every client near a spirit that finished an
  attack was disconnected (`ClassCastException` + Network Protocol Error). Ids moved to the private
  range 100–103, pinned by `CursedSpiritAnimationStateTest`; live re-check: a real attack cycle with a
  client attached, zero protocol errors, player HP 20 → 19, spirit scream fired.
- Live evidence (MCP lane, `analysis/mc_mcp.py`-driven frames + NBT): 20 naturally spawned cursed
  spirits scattered across the night map; variant NBT round-trip (`PROWLER` → `Variant:"prowler"`);
  textured renders, no missing-texture frames; Todo swap actually moved the player against a COMMON
  spirit and refused (`routed: false`, cooldown 0, positions unchanged) against the GREATER one;
  Megumi dogs sic killed the sicced spirit.

## What is where

- Codex note: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Cursed-spirits.md` (shape, seams,
  render stack, spawning gate incl. the NATURAL-only crowd cap, verification boundary, tuning pointers),
  linked from `00-MOC.md` (metrics bumped to 160/225/116).
- Accepted limits: `docs/KNOWN_ISSUES.md` → "Cursed spirits ship with accepted limits (slice 1)".
- Provenance: `docs/PROVENANCE.md` + `docs/THIRD_PARTY_NOTICES.md` (Sons of Sins import, permission
  recorded 2026-09-12, sha256 manifest re-verified).
- Pipeline artifacts: `.superpowers/rule-of-four/cursed-spirits/` — `implementation-plan.md` (rev 3),
  `progress.md` (every red, every root cause, the batched-mutation table), `port-map.md`,
  `live-protocol.md`, `review-*-report.md`, `review-spec.md`, `judge-followups.md`, `gate-log.txt`,
  `pr-body.md`. The derived third-party extracts (`assets/raw/**`, `decompiled/**`, javap dumps) stay
  on disk but are gitignored.

## If work continues here

- `feat/cursed-spirits` is **not yet pushed/PR'd** at the time of writing; the next step is the PR
  (title + body in Russian per AGENTS §12, `pr-body.md` is the draft) and then the release notes if the
  owner wants a version bump.
- Balance is a first pass: `CursedSpiritProfile` rows and `CursedSpiritVariant` weights are the tuning
  surface; nothing has been measured against long play yet.
- The live pass left the dev world with summoned parade spirits around (309, -60, 306) — harmless, the
  world is the MCP dev world.

# Session Handoff — shikigami slice shipped as v1.6.0 beta (2026-09-12)

## State — RELEASED

**`main` @ `6b01532` (`chore(release): 1.6.0`), tag `v1.6.0` pushed.**
Release (beta, jar attached): <https://github.com/grebeshok105/jujutsu-minecraft/releases/tag/v1.6.0>
PR #74 (squash-merged as `94408a7`): <https://github.com/grebeshok105/jujutsu-minecraft/pull/74>
The feature branch `feat/megumi-shikigami` is kept on the remote on purpose: the squash on `main`
collapses its commits, so the remote branch is the only copy of the incremental history.

Four Ten Shadows shikigami shipped on a new layer over the existing summons system: **Nue, Toad,
Rabbit Escape, Max Elephant**. The Divine Dogs are untouched apart from the deliberate free swap
(`TeardownReason.SWAPPED` + the widened recall predicate).

- `qualityGate` at the release commit: **BUILD SUCCESSFUL — 67 GameTest cases / 0 failures, 371 JUnit / 0**.
  Every new check was observed failing on its mutation first; the fable-judge pass re-ran four of those
  itself and its single refutation (a write-only expiry row) is fixed and re-proved.
- In game over the MCP lane: Nue dive (4.92) and soaked dive (7.38, x1.5), toad tongue (2.94) with the
  pull chain, rabbit swarm (bump 4.91 + slowness, anchor death/recall/expiry), elephant jet (3.76 +
  soak, owner inside the corridor spared, plant drift 0.000), plus a stationary-vs-walking frame pair
  proving the toad's rest-pose fix.
- The jar is installed in `D:/Games/instances/Jujutsu/mods/jujutsumod-1.6.0.jar` (the 1.1.0 build moved
  to `mods/_old/`, dated `.bak` copies kept alongside).

## What is where

- Codex note: `Jujutsu Kaizen/jujutsumod-codebase-codex/03-systems/Megumi-shikigami.md` (bodies, tunables,
  the clip-layering rule, the lifecycle table).
- Accepted limits (owner calls): `docs/KNOWN_ISSUES.md` → "Shikigami selection is in-memory, and the slice
  ships with accepted limits" (relog resets the selection; vanilla placeholder voices; no tongue geometry;
  the sic can out-range the tongue/jet; the elephant's jet is level; the rabbit run clip is asymmetric;
  the dog pounce yaw is filed as suspected-mirrored).
- Provenance: `docs/PROVENANCE.md` + `docs/THIRD_PARTY_NOTICES.md` (Sorcery Age ten-shadows import).
- Balance: one table in `SESSION.md`'s predecessor and in the PR body; every row lives in
  `MegumiShikigamiProfile` (recall/death/expiry cooldowns, damage, ranges, timings).
- Pipeline artifacts (local working material, untracked by the same convention as
  `.superpowers/rule-of-four/direwolf-visual/`): `.superpowers/rule-of-four/megumi-shikigami/` —
  `implementation-plan.md`, `plan-review.md`, `scout-1..4-report.md`, `review-*.md`, `qa-report.md`,
  `judge-report.md`, `block-fix-report.md`, `progress.md` (every measurement plus the oracle traps),
  `pr-body.md`.
- Lane traps learned here live in the project skill `.claude/skills/mcp-lane-launch/SKILL.md`.

## If work continues here

Nothing is blocked. Balance is deliberately draft — tuning means editing `MegumiShikigamiProfile` only.
The deferred items above are owner calls, not drive-by fixes.

---

# Session Handoff — feat/dev-lane-home (2026-09-09)

## State

- Branch **`feat/dev-lane-home`**, cut from `feat/archive-combat-hud` @ `7e2b40c`. Tip: `9cc6e77` — commits `90979a8` + `a7d2b9e` (mcpdev fixes), `ed70eed`/`e086aab` (docs/handoff), `9cc6e77` (AGENTS rules).
- Task: bring the MCP dev lane onto the current code in the main repository, 100% functional. Discovery: the lane code (src/mcpdev + `-PmcpSpike` gradle wiring + `prepareMcpSpikeRun`) was ALREADY in the current lineage — the old second-clone spike (`D:/WorkFlow/Jujutsu Minecraft/.worktrees/mcp-port-spike` @ 19f16a3, Aug 7) was just an old deployment. So the work was: run it here, reconcile, fix what was actually broken.
- Real bugs found and fixed: `90979a8` (provider trio + pure tick wait) and `a7d2b9e` (review P2: mid-wait player disconnect now fails fast instead of succeeding on the cleared-cooldown gate):
  1. `JujutsuModStatusToolProvider.toolClasses()` never listed the #66 L3 trio — `jujutsu_fixture_list`, `jujutsu_player_set_rotation`, `jujutsu_ticks_wait` existed as files but were absent from the live registry (the old lane had the same gap).
  2. `JujutsuTicksWaitTool`: `cooldownClear = player == null || …` completed a pure (no-player) tick wait on the first tick — instant return, waited_ticks 0. Gate now applies only with `player_uuid`; the wait revalidates player online status every tick.
- Local (untracked) setup: `run/saves/mcp-spike` copied from the old spike; `run/config/minecraft_fabric_mcp/config.json` = auth_required:false + log_level:debug.
- Docs: `.omp/RULES.md` dev-lane section rewritten (home = this repo + traps); `.claude/skills/mcp-lane-launch/SKILL.md` got a Home section; CURRENT_STATE.md record added. SESSION + skill commit pending on this branch.

## Verification (all live on current code)

- Lane launched from this repo: `gradlew.bat runClient -PmcpSpike -PmcpUpstreamJar=D:/WorkFlow/mcp-spike-scratch/…/minecraft-fabric-mcp-1.1.0+1.21.8.jar` (+ `JAVA_TOOL_OPTIONS=-Xmx3G`), quickPlay into the copied save, readiness lines on 8765/8766.
- Registry: 115 tools on 8765 (11 jujutsu), 6 client tools on 8766 — matches intent.
- Full battery green: mod_status (nobara), vessel_list (4), fixture_list (5), state_get, cooldowns_get/clear, vessel_select round-trip, ability_invoke (Todo aimed clap-swap: routed:true, zombie ↔ player positions exchanged, swap_momentum:true), player_set_rotation (aim), ticks_wait (pure 40 ticks = waited 40 / 2.18 s; uuid-mode exits instantly on clear cooldown), fixture_reset (20-step cleanup manifest), entity_summon/get/kill, command_execute, level time.
- mc-client: client_status (in_game, Player704), view_capture screenshots (run/lane-shot-*.png).
- Bonus in-world check of the HUD archive (feat/archive-combat-hud): Nobara selected with hammer held — NO ability strip above the hotbar, NO target panels/name labels on mobs; vision-verified on screenshots.
- qualityGate green after the fixes (~28 s). Lane shut down cleanly afterwards (never leave it running across sessions).
- Trap recorded: the singleplayer player UUID changes every client launch (offline profile) — always `entity_query @a` first.
- Vanilla-HUD scare resolved as a false alarm: hearts/food/XP in 1.21.8 render ABOVE the hotbar (left/right/center), not in the top corners — earlier top-corner crops were the wrong region, and the dev world is in Creative (vanilla hides hearts/food there). Survival proof (gamemode survival + /damage + real desktop screenshot): 10 hearts, 10 drumsticks, XP bar, hotbar all render; chat shows creeper kill. No regression — mod code touches only the crosshair.
- AGENTS.md rules updated on user request (`9cc6e77`): GitHub-only work (nothing unpushed across sessions), PRs with explicit Russian titles + «Для игрока» opening (release-note scheme) + technical part below, GameTests always run in the gate, autonomy section says never pester the user with repo-answerable questions.

## Next candidates

1. PR for this branch (Russian title/body; base = feat/archive-combat-hud stack).
2. Optional: wire `MC_MCP_TOKEN` sync if auth_required is ever turned on; the local config keeps auth off for dev.
3. Old second clone can be archived/removed when no longer needed (kept untouched for now).

---

# Session Handoff — feat/archive-combat-hud (2026-09-09)

## State

- Branch **`feat/archive-combat-hud`**, cut from `feat/codex-actualization` @ `ebef8d6` (that commit adds the AGENTS.md versioning/release rules requested earlier in the day).
- Task (user request + screenshot of the running game): take the marked combat HUD out of the active build and put it in a recoverable archive — bottom ability strip above the hotbar, the health/rank/nails card panel right of the target, the target-name label above it. No redesign, nothing deleted for good.
- Ground truth found first: the repo HEAD already carried the thin-bracket target HUD (4268c4a, 2026-08-25), but the deployed game jar (`D:/Games/instances/Jujutsu/mods/`, 2026-08-21 18:10) is exactly commit `5d95a0b` — the older glass-card look with the name pill above the target head, which is what the user marked. Consequences handled: the archive stores the current-tree implementation (the thing a rebuild would ship) and a verbatim glass-era snapshot of the three files that differ.
- Moved to `archive/combat-hud-v1/` (git mv, README = inventory + cut registrations + restore steps): `AbilityHud` (client/hud), `NobaraTargetHud/Layout/Anim/EspState/EspRanks` (client/character/nobara), 3 JUnit contracts; plus `snapshot-glass-5d95a0b-2026-08-21/` with the three glass-era files.
- Registration cuts: `JujutsuModClient` — `ability_hud` contribution + import; `NobaraClientDefinition` — `NobaraEspState.register()` + `nobara_target_hud` contribution (+2 imports). Four VfxDirector contributions stay live: `megumi_divine_dogs_cooldown`, `megumi_shadow_dive_veil`, `todo_pair_status`, `todo_stone_status`.
- Deliberately kept: ability input slots and keybinds, cooldown suppression (`ClientAbilityCooldowns`), the `hudSlots()`/`maxCooldownTicks()` seam on `CharacterClientDefinition` (restore point), shared render helpers (`WorldToScreen`, `UiEase`, SDF/MSDF), HUD assets and `esp.jujutsumod.rank.*` lang keys (inert data, restore-friendly).
- Docs updated in the same task (current-only discipline): Codex `00-MOC.md` metrics 191/85 → 185/82, `Uncertainties.md` (maxCooldownTicks → Resolved/MOOT), `Vessel-definitions.md`, `Nobara-combat-expansion.md`, `Nail-rendering.md`, `VFX-core.md` (four live contributions), `GUI-render-pipelines.md`; `docs/KNOWN_ISSUES.md` (E7 recount 9→8 files, new "Archived and recoverable / E16"); `docs/NOBARA_ESP_AND_MEGA_NAIL.md` ESP section marked archived; `docs/knowledge/CURRENT_STATE.md` record added.

## Verification

- `./gradlew.bat compileJava compileClientJava compileTestJava` green; `./gradlew.bat qualityGate` green (~52 s, JDK 21 — `JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current`).
- Jar proof: assembled `build/libs/jujutsumod-1.0.0.jar` contains none of the six archived classes; expected survivors (`VfxDirector`, `NobaraClientDefinition`, `MegumiCooldownHud`, `TodoStatusHud`, `HudSlot`) present.
- Deployed: jar copied to the Jujutsu instance (2026-09-09 17:10), md5 match.
- Client boot smoke: `runClient` reached the main menu ("Sound engine started", only benign MSDF/vanilla warnings) — no init-time errors from the cut registrations. NOT verified inside a world (selecting Nobara + embedding nails to eyeball HUD absence); absence is architecturally guaranteed by the class removal, but a world-level look remains open if wanted.
- Commits: `ebef8d6` (docs agents versioning), `0d512ca` (archive + cut), `4264fb0` (docs). Working tree has only untracked noise; `docs/knowledge/` stays untracked by convention.

## Next candidates

1. Optional in-world eyeball check after the user relaunches the instance (the old HUD should be gone; Todo/Megumi chips and abilities unchanged).
2. PR for this branch (title/body in Russian per convention), base per current open-PR stack (main is still `ce3d655`; the nobara/codex branches carry the newer lineage).
3. When a release is cut: per new AGENTS.md §13 rules (status alpha/beta/release; notes content-side only, Russian, emojis).

---

# Session Handoff — feat/codex-actualization (2026-09-04)

## State

- Branch **`feat/codex-actualization`**, 5 docs commits on top of `feat/nobara-target-hud` @ `4268c4a`.
- Goal: bring `Jujutsu Kaizen/jujutsumod-codebase-codex/` back to current — two audit waves (5 + 3 scouts) found ~19 PARTIALLY_STALE notes, 2 STALE, 0 broken links, 0 obsolete docs.
- Landed: dangerous-claims rewrite (combat-expansion trap 6.0→1.15, mega charge-24/flight-60, Boom symbol removed, curse bounds DONE, roster x4), HUD/VFX seams (49 ids, SIGN, 6 HUD contributions, AbilityHud + hudSlots strip, SDF_GLASS honest-state, thin-bracket HUD), orphan owners (particles, dimensions mixin, gametest tree, input translator, combat core, stone plans, Nobara families, Megumi policies), numbers (registries 3/12/24/3, Loom 1.17.17, claim-index, parity, MOC), maintenance shrink (how-to → rationale + skill link).
- Code issues found, NOT fixed (docs task): (1) `NobaraClientDefinition` leaves `maxCooldownTicks` at default 0 — AbilityHud skips her cooldown overlay (recorded in Uncertainties, needs owner call); (2) stale `replaced-entity animatable` code comment in `TodoVfxRecipes.java:79` vs live skinAnimation bridge.
- Verification: `audit_docs.py` green after every wave; `./gradlew.bat auditDocumentation` green via JDK 21. Full `qualityGate` not run — md-only diff, tests do not read the Codex. No subagents in the second half per user order; self-review instead of independent review (deviation from the goal's review step, recorded honestly).

---

# Session Handoff — nobara-target-hud branch (Aug 21 wave)

## State

- Branch: **`feat/nobara-target-hud`**, HEAD `d241544`+glass canary (5 commits on `main` `ce3d655`). PR open against main.
- Feature: Nobara's target ESP moved from the world-space billboard (vanilla Font inside `ProjectJjkNailRenderer`) to a screen-space HUD — `NobaraTargetHud` as one `VfxDirector.registerHudContribution`, name pill above the head + health/grade/nails glass card stack right of the target, projected through the new pure `ui/WorldToScreen` helper (JUnit-covered, no Minecraft imports).
- **Redesign wave (2026-08-21, active):** user rejected the first visual pass ("слишком бедный, плоский, черновой"). Second pass per reference screenshot, rule-of-four pipeline — 4 scouts done (pixelMetrics decoded the reference **1254x1254 raw RGBA**: health panel w:h=1:1.39 TALL not wide, real HEART glyph missing in code (plain orb), badge LIGHT capsule with bright edge (lum>240), lower panels WIDER than health (~1.44×), nails zone light lum≈248, edge light must be ~2× brighter). Plan-spec v3 in `.superpowers/rule-of-four/nobara-hud-refinement/plan-spec.md`: geometry **guiHeight-normalized** (unit=guiHeight/480f, PANEL_W=120f, stack=370f≤77% of 480 — v2's absolute-pixel stack of 505px didn't fit any realistic guiHeight), badge text switched to dark 0xFF2A3540 (light-on-light unreadable), visual acceptance via canary screenshot not grep. Blocks 1-4 = layout constants+JUnit, heart glyph, badge, lower panels+shader; Block 5 = integration (Main). Execution NOT started — user cancelled workers, spec is under review.
- `TargetEsp` lost `leaderNailEntityId`; `EspTargetData`/`renderEspBillboard`/`drawBadgeLine` deleted; nail renderer tripwire debt shrunk 5→3 refs (`SourceBoundaryTripwireTest`). MOC metrics: client_java=191, test_java=85.
- Animations: fade+slide appear (~3t), pop on nail-count change, HP accent pulse, FPS-independent HP chaser (real frame delta into `UiEase.approach`).
- Review wave: 2 P2 fixed (FPS chaser, vessel subtitle glyphs 3/B/T), 3 P3 fixed (javadoc, plan-spec sign note, CURRENT_STATE dated-bullet split), 1 accepted: `ownedByLocal` accent no longer gated on the ESP snapshot — own nails always draw the orange pulse, even when playing a non-Nobara vessel or before snapshot refresh (cosmetic, smoke item).
- `docs/knowledge/CURRENT_STATE.md` stays **untracked** (project memory): it names a docs subfolder that the documentation audit forbids, so committing it fails `auditDocumentation`. The 2026-08-21 record lives in KNOWN_ISSUES E14 instead.

## Verification

- `./gradlew.bat qualityGate --rerun-tasks` green on `d241544`: 296 JUnit / 0 fail, 34 GameTest, 29 JavaExec, doc + jar-isolation audits.
- Counts verified by tree scan: main=126, client=191, test=85.
- NOT yet verified in-game (gate proves none of this): card layout/scale by eye, badge offsets at GUI scales 1/2/4, edge-of-screen clamp feel, accent-on-non-Nobara cosmetic case.

## Deploy

- Jar for the game instance: `./gradlew.bat assemble` → `build/libs/jujutsumod-1.0.0.jar` → copy to `D:/Games/instances/Jujutsu/mods/`, md5-compare.

## Next candidates

1. Visual polish pass of the target HUD from real screenshots.
2. New vessel (Yuji / Maki — add-vessel skill)
3. #18 localization parity (quick win); #26 VFX delivery polish
4. #21 remaining slices
