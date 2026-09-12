# Cursed Spirits — pipeline progress (rule-of-four)

Task: full hostile cursed-spirit system — 3 gameplay tiers, multiple visual variants
from the Sons of Sins asset pack, spawn system, combat-core integration, tests, docs,
live MCP in-game verification.

Branch: `feat/cursed-spirits` (to be cut from `main` @ `bd0c4d6`).

## Assets (working material)

- `assets/` — archive extracted (raw jar files: classes, textures, sounds, sounds.json, lang, particles; MANIFEST.csv; README_RU.txt; references/)
- `decompiled/` — CFR 0.152 decompile of all 245 classes (models, animations, renderers, entities, behaviors)
- `tools/cfr.jar`, `tools/sos-classes.jar` — decompiler + class bundle
- `references/tier_reference.png` — owner's visual tier reference
- `analysis/` — throwaway analysis scripts (scout-3)

## Phase status

| Phase | Status | Notes |
|---|---|---|
| 0 — 4 scouts | DONE | reports saved: scout-1..4-report.md (verbatim) |
| 1 — plan | DONE | `implementation-plan.md` rev 2 (post-review): 5 blocks, R1..R30 traceability, D1–D8 decision records, frozen sound/clip tables |
| 1.5 — 3 plan reviews | DONE | all three reviewers delivered; every P0 closed in rev 2/3 (details: `plan-review.md`); one truncation finding rejected with evidence |
| 2 — workers | RUNNING | Block 1 DONE+verified; Block 2 (ServerCore): `API landed` at commit c0d4521, JUnit+GameTests in flight; Block 3 (ClientRender): dispatched early in phased form (dependency-free animation port first), Phase B unblocked by `API landed`, `compileClientJava` builds; Block 4 (SpawnIntegration): dispatched on `API landed`, serialized edits gated on `block-2 report filed`; Main (Block 5): sound-symmetry test landed + green, integration GameTests pending |
| 3 — reviewers + QA | pending | auto-start after green barrier |
| 4 — adjudication + fixes + judge | pending | |
| 5 — capture + docs + PR + report | pending | main owns Block 5 |

## Orchestration rules (learned this pipeline)

- **Lane ownership (owner decision 2026-09-12):** workers never run the in-game lane (`runGameTest`, `runClient`, MCP dev lane) — it is a single global resource (ports 8765/8766, shared build/run dirs) and one cycle costs 3–8 minutes; parallel workers on it serialize the whole pipeline. Workers may run compile + JUnit + unit-level red-proofs, and they must still WRITE their in-world GameTest scenarios with declared oracles; Main runs the lane once per integration batch and routes failures back to the owning block with the log excerpt.
- Red-proofs are batched: 2–3 independent mutations in one build → one lane run → record all failures → restore → one final green run.
- Every gate a worker waits on must be an explicit named hub message from Main; "waiting for a sibling" is not a gate and produced a 23-minute stall.

## Deviation log (orchestration)

- **Architecture guard extended (Main).** `VesselBoundaryTest.everyPackageUnderAVesselParentNamesARegisteredVessel` failed on `jujutsu.mod.client.render.cursedspirit` (cursed spirits are a shared mob system, not a vessel). Fixed by pinning a scoped, documented `SHARED_SEGMENTS_BY_PARENT` exception (`render → cursedspirit`, keeping the existing `vfx → world` case) instead of a global allowlist — `character.world`/`render.world` still fail closed. Test green (11/11); recorded for the Codex note.

- **U1 root cause (Main, javap).** `GameTestHelper.makeMockServerPlayerInLevel()` returns an anonymous `GameTestHelper$2 extends ServerPlayer` that **overrides `gameMode()` with a stub**; `Player.isCreative()` reads `gameMode()`, so every in-level mock is permanently creative for targeting and `setGameMode` mutates only the ignored `ServerPlayerGameMode` field. Clean fix (routed to ServerCore): build the victim as a plain `new ServerPlayer(server, level, profile, ClientInformation.createDefault())`, `setGameMode(SURVIVAL)`, then `level.addNewPlayer(victim)` (public; populates `level.getPlayers`, which the player selector uses). Re-opens R3/R4/R5/R24 GameTest coverage plus their owed per-scenario red-proofs.
- **Light-gate conflict settled (Main, javap).** `PathfinderMob` overrides `Mob.checkSpawnRules` with `getWalkTargetValue(blockPosition(), level) >= 0` and `Monster.getWalkTargetValue` is the light-based path cost ⇒ the light gate lives in `super`; ServerCore's "nothing gates light" relay was wrong, Block 4's lit-cell assert stands (relayed to Block 4).
- **ClientRender stall (23 min).** The worker parked waiting for a "ServerCore green" signal that was not part of the gate contract; woken with an explicit greenlight once Block 2 filed.
- **Phased early Block 3 dispatch (owner-requested speed-up).** The plan gated Blocks 3/4 on Block 2's `API landed`. Block 3's bulk (9 animation classes, pure vanilla APIs) needs nothing from Block 2, so it was dispatched before the gate with an explicit phase split (Phase A now / Phase B after `API landed`), and Block 2 was nudged to land commit-1 first. Recorded here per the plan's own gate discipline.
- **Block 4 cross-block request.** `CursedSpiritEntity.checkSpawnRules` lives in Block 2's file; Block 4 sends Main the snippet and Main routes it to Block 2 instead of letting two workers edit one file.
- **Sound symmetry ownership.** `CursedSpiritSoundSymmetryTest` (Block 5) landed early because `API landed` already froze its inputs; its registry-presence half moves to Block 5's integration GameTest (unit-test registries are frozen before registration).

## Review findings folded into rev 2 (adjudication summary)

- **Cycle B2→B4 (P0, completeness):** Block 2's tree no longer calls `CursedSpiritSpawnIntegration`; Block 4 owns that one-line edit to `CursedSpirits.java` after `block-2 report filed`.
- **Deterministic gates:** `API landed` (B2→dispatch B3/B4), `first rig landed` (B3→main probe), `block-2 report filed` (B4 + main serialized edits), `block-4 report filed`.
- **Long-line truncation (P1, completeness — PARTIALLY REJECTED):** reviewers' readers clip lines >768 chars; the six "truncated sentences" are complete in the file (verified lengths 850–1244). Root cause was line length, not missing content — all long lines are now wrapped (≤700) so readers see full contracts.
- **Frozen tables added:** per-variant sound channel table (34 channels) + exact per-clip bone map with looping flags (from `analysis/rig_audit.py`).
- **AI target acquisition (P0, verifiability):** Step 6/7 name `NearestAttackableTargetGoal<Player>` + `HurtByTargetGoal` and the attack goal reads `getTarget()`; Step 10 adds premise asserts + `getTarget()==victim` oracle and forbids `spawnWithNoFreeWill` for the attacking mob.
- **Animation sync (P1):** attack/scream states start on both sides via `handleEntityEvent` broadcast (vanilla Warden pattern); idle ticks both sides.
- **T2/T3 oracles (P0):** strike-step velocity burst (COMMON) + AoE in/out-radius pair (GREATER) + relative-position asserts.
- **R7/R9/R16 verifiers:** Nobara aimed/mis-aim routed cases; Todo melee + Black Flash delta; TargetResolver comparator JUnit (B2) + live ranking smoke (B5).
- **Block 3 contract tests:** exact clip-key sets + exact bone counts + looping flags + five red-proofs (rename / channel drop / key swap / looping flip / texture delete); PROWLER-first fail-fast commit + live probe.
- **Block 4 purity:** pure `core(blockLight, skyLight, difficulty, roll, nearby)` + thin level adapter; vanilla darkness roll semantics read from mappings before pinning numbers; local crowd cap `MAX_SPIRITS_NEARBY = 10`.
- **MCP toolchain (P1):** `tools/list` confirmation first; `/summon` via `command_execute` fallback; mob frozen by summoned uuid; NO stagger-via-`state_get` and NO raycast oracles — HP/velocity deltas and numeric frame analysis; sounds manual-only.
- **Variant persistence:** string id (not ordinal) + tier validation on load + JUnit round-trip.
- **R19/R30 numbers:** soak falsification thresholds recorded in Block 4 acceptance (census cap, TPS ≥ 18, zero jujutsumod ERROR).
- **D8 recorded:** the brief's per-tier live checks are honored as a structured T1→T2→T3→combined sequence in the final pass + the early single-rig probe, not three separate builds.


## Environment facts (barrier evidence)

- Repo: `D:/WorkFlow/jujutsu-minecraft`, branch `feat/cursed-spirits` (from `main` @ `bd0c4d6`).
- Gradle 9.5.1; builds need `JAVA_HOME=C:/Users/KOMP1/scoop/apps/temurin21-jdk/current` (default shell JVM is 17 — always export JAVA_HOME for gradle).
- MC 1.21.8 merged jar for javap: `C:/Users/KOMP1/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/1.21.8-loom.mappings.1_21_8.layered+hash.2198-v2/...jar`.
- Fabric `BiomeModifications.addSpawn(Predicate, MobCategory, EntityType, int, int, int)` verified present in fabric-biome-api-v1-16.1.0 jar.
- MCP lane prerequisites **verified 2026-09-12**: upstream main jar `minecraft-fabric-mcp-1.1.0+1.21.8.jar` present in `D:/WorkFlow/mcp-spike-scratch/upstream/versions/1.21.8/build/libs/` (alongside javadoc/sources jars); `run/saves/mcp-spike` present; `run/config/minecraft_fabric_mcp/config.json` = host 127.0.0.1, port 8765, `auth_required: false`, log_level debug; `run/mods/` empty (upstream jar comes via `-PmcpUpstreamJar`); ffmpeg/ffprobe on PATH.
- Asset audio: 80 `.ogg`, 78 mono / 2 stereo (stereo are `sin_summon`/`sins_redwolf`-class stingers, not in the frozen picks); sample rates 44.1 kHz (one 48 kHz). All frozen picks verified mono → positional audio fine, no transcode.
- Decompile reference: `.superpowers/rule-of-four/cursed-spirits/decompiled/**` (CFR 0.152) + `references/main_sins_javap/**`.

## Phase 4 — review adjudication + fix wave (2026-09-12)

All four reviewers filed. Verdicts: Block1 GO (2 P2 docs — fixed `99347a4`), Block2 GO (no P0/P1; P2 truncated report, 2×P3), Block3 GO (3×P3 hygiene), Block45 NO-GO-until-fixed (P1 sweep fratricide, P1 missing scenarios, 3×P2 oracle gaps).

Adjudication: `review-spec.md` (CONFIRMED / REJECTED / MANUAL table). All findings CONFIRMED except the accepted-limit restatements; nothing rejected outright.

Fix wave (3 workers, disjoint files, no lane; Main ran the batch):
- `e19f32c` FixSpawnSweep — owner-scoped cleanup, oracles unchanged (P1 fratricide closed).
- `a9be558` FixClientP3 — single bake, independent clip-count literals, exact clip lengths + one batched red-proof (Block 3 P3s closed).
- `3b0759c` FixSmallServer — spawn-test premises scoped to owned bodies + production radius; strike-time reach guard (no LOS re-check, documented); VariantTest standalone bootstrap proven standalone 7/7 (Block 2 P3 + Block45 P2 closed).
- `0fb75f9` FixIntegrationOracles — R7 mis-aim, R8 straw-doll ritual over a marked spirit, R16 live ranking; Hairpin strict damage + nail premise; per-tier remnant proof with tag assert + nail sweep; Todo forced-vs-unforced Black Flash differential (all six Block45 findings closed, lane-pending).
- `f6d7c7a` ServerCore — block-2 report tail restored (red-proofs, lane counts).
- `114c8e2` (Main) — Block-1 contract test now pins the shipped file sets exactly (Block1 P3).
- `ff39f0c` (Main) — Codex note `03-systems/Cursed-spirits.md` + MOC link + accepted limits in KNOWN_ISSUES (MOC metrics bumped in the same commit: 160/225/116).

Barrier: `qualityGate` run as one batch by Main after the wave; failures route back to the owning fixer with the log excerpt (lane rule §16).

## Barrier runs and the four red root causes (Main, single-batch lane)

`qualityGate` attempt 1: **88 GameTests, 4 red** — the fratricide was gone, the remainder were real.

| Test | Observed | Root cause | Fix |
|---|---|---|---|
| `spawnGateRefusesLightPeacefulAndCrowdButAllowsDark` | `worldNearby=13/10 blockLight=0` | The cap conjunct counts the WHOLE level within 48 blocks, so sibling arenas' bodies red the "dark allows" half. Inherent to the shared GameTest level. | Production semantics tightened: the crowd cap applies to `EntitySpawnReason.NATURAL` only (spawner/command/egg placements are not crowd-gated). Light halves now call the gate with `SPAWNER`, so they are deterministic; the cap half asserts a refusal, which pollution can only reinforce. |
| `megumiDogSicPunishesTheSpirit` | `UNHANDLED_FAILURE` at tick 24 | The spirit spawned at rel (7,1,1) — **off the 6×6 stone pad** — and fell out of sight/range, so the sic resolver had nothing to lock. | Spirit moved onto the pad at (6,1,1) + `freezeGround` + cast-tick premises (alive, in `SIC_RANGE`, LOS). |
| `nailImpactMarksTheSpiritAndDirectedHairpinPunishes` | chain premise `>= 1, actual 0` at tick 4 | `isEmbedded()` turns true one tick BEFORE `EmbeddedNailRegistry.track`, and the directed chain is built from the registry — casting in that window returns SUCCESS and does nothing. | The cast waits for `marks ≥ 1` AND a tracked chain; the deadline assert names whichever premise never materialized; the aim is re-taken at the body's current centre before the cast (the seed comes from the look vector). |
| `twoNailImpactsMintCurseRemnantsForEveryTier` | `marks=1 hp=12.0 nails=2 embedded=2 here=1 ground=1` | Two stacked causes, proven by instrumentation: (a) the second shot used a fixed tick, landing inside the victim's hurt-immunity window; (b) the FIRST nail's knockback (0.9) slid the frozen body **past the pad edge** (rel x 7.6 > 6), it fell into the structure-border trench (`below=barrier`), and the follow-up nail flew into the pad's edge block (`stone @ rel y=1`). | Second launch fires 26 ticks after the mark was seen (clears immunity), and `containOnPad` puts the body back on its pad spot with a "stands on stone" premise before the shot. |

`meleeReachRespectsHitboxEdge` also red on one run (`firstSeen=none@-1`): the acquisition scan simply had not fired inside the 30-tick window on that layout. The poll now runs to tick 60 (the test's oracle is the strike-reach, not the scan timing) and the failure message carries `canAttack`, `noAi`, `followRange` and the level's player count.

After the fixes: **88/88 GameTests green** in the single-batch lane (`build/test-results/gametest/junit.xml`), then the full `qualityGate` re-run for the barrier artifact.

## QA + judge follow-ups (Phase 4 close-out)

QA verdict GO-WITH-CAVEATS (420 JUnit, 88 GameTest, docs audit, provenance 5/5 hashes). Judge: every load-bearing claim VERIFIED; F1–F4 adjudicated in `judge-followups.md`. QA's P1 (cap-half oracle switched to SPAWNER by a bad blanket restore) was found by them and fixed inline by Main — cap half is NATURAL again, light halves stay SPAWNER.

**P2-a — the five never-redded scenarios, closed by one batched mutation lane** (six mutations in one build, one lane run, all restored afterwards):

| Mutation | Red it produced |
|---|---|
| `ProjectJjkNobaraProfile.RESONANCE_DAMAGE` 28.0 → 0.0 | `strawDollRitualBurnsMarkedSpiritAndSparesUnmarked` |
| `MegumiProfile.SIC_RANGE` 20.0 → 4.0 | `sicResolvesCentredFarSpiritOverNearEdgeGraze` **and** `megumiDogSicPunishesTheSpirit` |
| `TodoProfile.BLACK_FLASH_DAMAGE_MULTIPLIER` 1.75 → 1.0 | `todoForcedBlackFlashOutDamagesTheSameUnforcedMelee` |
| `CursedSpiritVariant.WALKING_BED` ambient → `CURSED_WISTIVER_AMBIENT` | `everyCursedSoundRowIsRegisteredOnTheLiveServer` (lane) + `CursedSpiritSoundSymmetryTest` 2/3 red with `expected: <jujutsumod:cursed.walking_bed_ambient> but was: <jujutsumod:cursed.wistiver_ambient>` (P3-b) |
| `ProjectJjkMegaNailRuntime` empty-chain guard → SUCCESS | `nobara_ability_result_game_tests_unhandled_failure_still_shows_generic_fallback` |
| `ProjectJjkMegaNailRuntime` mis-aim guard → disabled | `megaNailMisAimFailsWithoutCoolingAnySlot` |

Lane result with all six live: **7 required tests failed**, every one named above. After restore: 88/88 lane green + full JUnit green.

**P3-a** — the FixClientP3 batched red-proof, quoted: `length for PROWLER/IDLE ==> expected: <1.06> but was: <2.12>` and `clip count for PROWLER ==> expected: <5> but was: <4>` (one edit mutated the count literal and the length literal together).
**P3-c** — `VesselBoundaryTest.sharedSegmentExceptionsAreExactlyTheTwoJustifiedPairs` now pins the exact two-entry exception map, so a third entry fails instead of silently passing.
**P3-e** — `block-3-report.md` wording corrected: WISTIVER GRAVE exists upstream and is not ported (no driver state), matching the code and `KNOWN_ISSUES`.
