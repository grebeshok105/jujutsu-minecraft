# Block 2 report — server core (entity, tiers, variants, AI, seams, tags)

## What was done

Steps 1–11 of `## Task 2 (Block 2)` implemented, plus the follow-up hardening from the
lane-driven debug loop (all inside my ownership row). New package `jujutsu.mod.cursedspirit`
(`CursedSpiritTier`, `CursedSpiritTierStats`, `CursedSpiritProfile`, `CursedSpiritVariant`,
`CursedSpiritEntity`, `CursedSpiritAttackPolicy`, `CursedSpiritAttackGoal`, `CursedSpirits`),
new combat seams (`StaggerResistant`, `CombatTags.BOOGIE_WOOGIE_IMMUNE`), seam edits
(`CharacterCombatModifiers.adjustedStaggerTicks`, `TodoBoogieWoogieRuntime.isEligibleTarget`),
3 entity types, 34 `CURSED_*` sound rows (constants + registration), both tag JSONs,
`CursedSpirits.registerServerHooks()` (attributes only) + 1 line in `JujutsuMod`,
6 JUnit classes, `CursedSpiritGameTests` (12 scenarios) + `CursedSpiritTestFixtures`,
`fabric.mod.json` first edit. `SCREAM_DURATION_TICKS = 12` per rev-3 pin.

Base commits on `feat/cursed-spirits` (branch moved while I worked — Block 3/4 landed):
`d723f31` (server tiers/variants/AI/seams), `dc4ab63` + `ddf5001` (Block 3),
`45b213a` (Block 4 spawn rows). My commits: see bottom.

Follow-up hardening in this session (same row, no new components):
- **U1 closed (was the block's open risk): victims are now directly-constructed
  `ServerPlayer`s.** The GameTest mock factory returns a subclass whose `gameMode()` is a
  hard CREATIVE stub, so `NearestAttackableTargetGoal<Player>` never acquires the body.
  Fixture now builds `new ServerPlayer(server, level, profile, ClientInformation…)`,
  routes through `playerList.placeNewPlayer` (fresh loopback `Connection` + `EmbeddedChannel`,
  otherwise gamemode flips/packets NPE on the null connection), teleports AFTER placement
  (the respawn path relocates the body to world spawn — no LOS, no acquisition), calls
  `setClientLoaded(true)` (bytecode-verified on 1.21.8: `ServerPlayer.isInvulnerableTo`
  returns true while `!hasClientLoaded()`, and no login packet ever arrives), pins the
  `Entity.invulnerable` flag off, and premise-asserts hurtable + hostile difficulty.
  All 12 scenarios green in the murder-free lane.
- **R4 re-pinned 0.30 → 0.10 with ground truth.** The 0.35 `strikeStep` impulse is sampled
  post-friction at ~0.19 against a 0.0 standing baseline (body spawns in reach, never walks;
  ground walk caps at the 0.24 speed row, so 0.30 was unreachable-by-construction).
  Red-proof: `strikeStep → 0.0` gives peak `0.0` → oracle red.
- **R24 acquisition premise hardened.** Single-tick assert → poll ticks 8–30 (target-goal
  scan interval makes any single early tick racy); failure path discards bodies so two
  victims can't linger into neighbour arenas; message names `nearRemoved/farRemoved` (now
  also `spiritRemoved`) so a null target self-explains.
- **R14 swap test:** Slowness-freeze both spirits (bodies wandered between setup and cast →
  aim/LOS/range flake) + LOS + in-range premises before the second cast.
- **`CursedSpiritAttackGoal.canContinueToUse` override** (deliberately looser than `canUse`:
  a momentary LOS blip mid-swing must not abort the strike; only dead/gone ends it).
- **`checkSpawnRules` comment corrected** (no behaviour change): javap shows super is
  PathfinderMob's walk-target gate (light-sensitive via Monster's cost override), so darkness
  flows through it; the explicit PEACEFUL gate stays load-bearing. Crowd cap is Block 4's row.

## Files

- New main: `cursedspirit/CursedSpirit{Tier,TierStats,Profile,Variant,Entity,AttackPolicy,AttackGoal}.java`,
  `cursedspirit/CursedSpirits.java`, `combat/StaggerResistant.java`, `combat/CombatTags.java`
- Modified: `character/CharacterCombatModifiers.java`, `character/todo/TodoBoogieWoogieRuntime.java`,
  `registry/JujutsuEntities.java`, `registry/JujutsuSounds.java`, `JujutsuMod.java`,
  `data/jujutsumod/tags/entity_type/{boogie_woogie_immune,resonance_remnant_curse}.json`
- New tests: `src/test/.../cursedspirit/{CursedSpiritProfileTest,CursedSpiritVariantTest,
  CursedSpiritAttackPolicyTest,CursedSpiritTagContractTest,CursedSpiritRegistryTest,
  CursedSpiritTargetResolverTest}.java`
- New gametest: `src/gametest/.../{CursedSpiritGameTests,CursedSpiritTestFixtures}.java`;
  `src/gametest/resources/fabric.mod.json` keeps Block 5's `CursedSpiritIntegrationGameTests`
  line (theirs — preserved, left uncommitted by me).
- Session diff vs HEAD (`45b213a`): `CursedSpiritGameTests.java` (+46/-11: R4 re-pin, R24 poll,
  R14 freeze+premises), `CursedSpiritTestFixtures.java` (direct-player victims),
  `CursedSpiritAttackGoal.java` (+11: `canContinueToUse`), `CursedSpiritEntity.java`
  (comment-only: spawn-rules correction).

## Acceptance (Факт lines)
- Факт: `gradlew.bat compileJava compileClientJava compileTestJava compileGametestJava
  --no-daemon --max-workers=1 --no-watch-fs` → BUILD SUCCESSFUL in 9s (final tree).
- Факт: `gradlew.bat test --tests "jujutsu.mod.cursedspirit.*" --tests "jujutsu.mod.combat.*"`
  → BUILD SUCCESSFUL, 46 tests, 0 failures, 0 errors (per-class XML counts independently
  confirmed by ReviewBlock2: AttackPolicy 7, Profile 5, Registry 4, ResourceContract 5,
  SoundSymmetry 3, SpawnRules 5, TagContract 2, TargetResolver 2, Variant 7,
  CombatStaggerClear 2, SafeBodyPlacement 4).
- Факт: `runGameTest` in the murder-free lane → 13 cases, 0 failed
  (`build/test-results/gametest/junit.xml`): all 12 `CursedSpiritGameTests` scenarios green
  (R3 lesser-acquires, R4 step-burst, R5 greater-slam, R11 two-sources, R12 stagger window,
  R13 knockback order, R14 swap exchange, R15 greater deny, R24 reach edge, R25 lethal
  removal, tags+variant sync, follow-range pins) + `minecraft:always_pass`. Observed by me
  pre-compaction; final tree differs from that lane's tree by whitespace + one diagnostic
  string only (diff-verified), compile re-verified after.
- Факт: full lane on final code → 85 cases, 9 failed, all 9 spirit-related (5 mine + 4
  Block-5 integration), 76/76 others green — re-read from
  `build/test-results/gametest/junit.xml` just now, same 9 names as observed pre-compaction.
- Факт: R1 registry contract green — 3 ids in `BuiltInRegistries.ENTITY_TYPE`, MONSTER x3,
  hitboxes 0.85x1.0 / 0.75x1.9 / 1.35x2.4, `canSerialize()` true x3, no `.noSave()`.
- Факт: R20 linkage green — `mutatedProfileRowMovesDerivedDamage` (test-side mutated row
  moves `primaryDamage`/`aoeDamage`).
- Факт: R16 green — `CursedSpiritTargetResolverTest` through `resolveForTests`.
- Факт: U1 closed — direct-`ServerPlayer` victims via `placeNewPlayer` (loopback
  `Connection` + `EmbeddedChannel`), teleport AFTER placement, `setClientLoaded(true)`,
  `setInvulnerable(false)` + hurtable premise; all melee oracles land.
- Факт: U2 closed — `CursedSpiritIntegrationGameTests` compiles and runs in the lane
  (its 4 failures are fratricide victims, not compile errors).
- Факт: goal-registration fix — `spawnSpirit` uses `helper.spawn` (full AI). Stepwise probes
  showed create→setPos→setNoAi(true)→finalizeSpawn→addFreshEntity keeps goals=5 while
  `spawnWithNoFreeWill` returns an empty goalSelector (`setNoAi(false)` cannot bring
  stripped goals back); `Mob`-ctor `registerGoals` runs before subclass fields init, so the
  wipe is in the helper path, not ctor order.
- Факт: R4 ground truth — 0.35 impulse samples at ~0.19 post-friction (0.35 x 0.546 =
  0.1911, the ground-friction retention 0.6 x 0.91); walk caps at the 0.24 speed row;
  standing baseline is 0.0, so the 0.10 floor is burst-discriminating.

## Red-proof records (mutation → observed failure → restored)
Batch 1 — profile combat rows (one lane, 3 mutations, 3 scenarios red, then green):
1. LESSER `attackDamage` 3.0 → 0.0 → `lesserAcquiresVictimAndDealsMeleeDamage` FAILED
   ("lesser damaged the victim by tick 120: expected <hp < 20.0>, actual <20.0>") →
   restored, green.
2. COMMON `strikeStep` 0.35 → 0.0 → `commonStrikeCarriesStepBurst` FAILED (peak speed 0.0
   vs the >= 0.10 assert) → restored, green.
3. GREATER `attackDamage` 8.0 → 0.0 → `greaterSlamSplashesBodiesInsideRadius` FAILED
   ("greater struck the victim by tick 200: expected <hp < 20.0>, actual <20.0>") →
   restored, green.
Batch 2 — profile reach/HP/stagger/KB rows (one lane, 4 mutations, 4 scenarios red):
4. COMMON `attackReach` 2.5 → 0.5 → `meleeReachRespectsHitboxEdge` FAILED ("near victim
   struck by tick 120: expected <hp < 20.0>, actual <20.0>") → restored, green.
5. COMMON `maxHealth` 45.0 → 50.0 → `twoDamageSourcesBothReduceHealth` FAILED ("common
   starts at profile health: expected <45.0>, actual <50.0> on tick 2") → restored, green.
6. COMMON `staggerMultiplier` 1.0 → 10.0 → `staggerAppliesThenClearsAfterWindow` FAILED
   ("stagger cleared after the 20-tick window: expected <false>, actual <true> on tick
   26") → restored, green.
7. LESSER `knockbackResistance` 0.0 → 1.0 → `knockbackDisplacementOrdersByTier` FAILED
   (equal-impulse assert observed |0.0 - 1.0| = 1.0 vs the < 0.05 band) → restored, green.
Batch 3 — cross-tier plumbing (one lane, 4 mutations, 6 scenarios red):
8. `followRange` LESSER<->COMMON swap (16.0<->24.0) → `profileFollowRangesOrderByTier`
   FAILED ("follow ranges tier-ordered: expected <16 < 24 < 32>, actual <24.0 < 16.0 <
   32.0>") → restored, green.
9. `boogie_woogie_immune.json` → lesser only → `entityTypeTagsAndVariantSync` FAILED
   ("lesser swappable: expected <false>, actual <true>") + `greaterRefusesSwapAtomically`
   inverted ("greater swap cast result: expected <false>, actual <true>") +
   `lesserAndCommonSwapPositions` commit2 refused ("common swap cast result: expected
   <true>, actual <false>") → restored, all green.
10. `setVariant` neutered to no-op → tags+sync FAILED on "setVariant sticks" → restored.
11. Lethal `Float.MAX_VALUE` → 5.0f → `lethalDamageRemovesBody` FAILED at tick 26 ("dead
   body removed: expected <isRemoved=true>, actual <false>"). Note: my mutation edit also
   deleted the tick-2 kill assert, so the red surfaced at the removal oracle — the scenario
   as a whole still failed on sublethal damage; assert restored verbatim.
12. Same lane: `meleeReachRespectsHitboxEdge` acquisition premise read null at tick 20
   with no removal state captured then — unexplained at the time; bounded by the later
   13/13 green run and the tick-30 + removal-state hardening.
Unit-level (kept from the earlier report, all restored → green): COMMON weight 20→100 →
`spawnWeightsOrder` FAILED; `aoeMemberIndices` ==/!= flip → `aoeMembershipKeeps` FAILED
("expected: <[1]> but was: <[0]>"); immune tag emptied → `boogieWoogieImmuneTagContainsOnlyGreater`
FAILED ("greater immune: []"); pre-fix live probe `/summon lesser_cursed_spirit` →
`IndexOutOfBoundsException` at `defaultVariantOrdinal` (super-ctor before `tier` assignment),
fixed via `define(DATA_VARIANT, 0)` + null-safe `variant()` + lazy goal stats.

## Risks / unresolved
- **Fratricide (cross-test interference, NOT my file): Block 4's `CursedSpiritSpawnGameTests`
  sweep discarded neighbour structures' spirits.** Evidence on final code: full lane 85
  cases / 9 failed, all 9 spirit-related (mine: R13 "lesser moved: expected <> 0>, actual
  <0.0>" = discarded body; R14 commit2 swap refusal; R24 null target; R3/R5 no damage by
  deadline; plus 4 Block-5 integration), 76/76 others green; murder-free lane 13/13 green
  on equivalent logic. R24's enriched message excluded victim-removal (`[nearRemoved=false
  farRemoved=false]`). Fix (`e19f32c`, scope cleanup to own bodies) landed after my session
  — Main's barrier lane re-verifies.
- **R24 residual:** one null-target observed in a murder-free lane (tick-20 deadline era);
  deadline extended 20→30, green since (one run). If the barrier lane shows null with
  `spiritRemoved=false`, the target-scan timing needs a longer window, not oracle loosening.
- **Relay, not mine:** F2 `CursedSpiritVariantTest` fails standalone (missing `Bootstrap`
  before entity class init; order-masked in suite) — needs `@BeforeAll Bootstrap.bootStrap()`.
- **Relay, design call for Main:** F3 `strike()` has no strike-time reach/LOS re-check
  (documented `canContinueToUse` looseness); accept as melee forgiveness or gate STRIKE on
  `inReach`.
- **Harness note:** renaming a `fabric.mod.json` entrypoint with a `_TEMP_DISABLED` suffix
  does NOT exclude the class (renamed entries still executed — observed 85-case lane).
  Isolate via entrypoint removal, never rename.
- NBT round-trip: `resolveLoadedVariant` unit-tested (unknown/cross-tier re-roll, valid
  survives); no full save/load cycle in-world — in-world `setVariant` sync covered instead.

## Scope-Growth
None triggered. No new component, dependency, or design change; cross-block requests served
inside my row (PEACEFUL gate + Block 4 cap conjunct in `checkSpawnRules`; 7-arg `diagnostic`
overload for Block 5).

## Commits
On `feat/cursed-spirits`, never pushed: `35846ba` (feat: harden spirit GameTests + swing
continuity — 4 src files), `11e8541` (docs: this report). Left uncommitted deliberately:
Block 5's `fabric.mod.json` Integration line + untracked
`CursedSpiritIntegrationGameTests.java`.