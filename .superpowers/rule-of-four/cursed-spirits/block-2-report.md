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
- Session diff vs HEAD (`45b213a`): `CursedSpiritGameTests.java` (+46/−11: R4 re-pin, R24 poll,
  R14 freeze+premises), `CursedSpiritTestFixtures.java` (direct-player victims), 
...[truncated 7375 chars]