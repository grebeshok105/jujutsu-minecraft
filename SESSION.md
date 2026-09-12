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
