# Block 2 report — server core (entity, tiers, variants, AI, seams, tags)

## What was done
Steps 1–11 of `## Task 2 (Block 2)` implemented. New package `jujutsu.mod.cursedspirit`
(`CursedSpiritTier`, `CursedSpiritTierStats`, `CursedSpiritProfile`, `CursedSpiritVariant`,
`CursedSpiritEntity`, `CursedSpiritAttackPolicy`, `CursedSpiritAttackGoal`, `CursedSpirits`),
new combat seams (`StaggerResistant`, `CombatTags.BOOGIE_WOOGIE_IMMUNE`), seam edits
(`CharacterCombatModifiers.adjustedStaggerTicks`, `TodoBoogieWoogieRuntime.isEligibleTarget`),
3 entity types, 34 `CURSED_*` sound rows (constants + registration), both tag JSONs,
`CursedSpirits.registerServerHooks()` (attributes only) + 1 line in `JujutsuMod`,
6 JUnit classes, `CursedSpiritGameTests` (12 scenarios) + `CursedSpiritTestFixtures`,
`fabric.mod.json` first edit. `SCREAM_DURATION_TICKS = 12` per rev-3 pin.

Commits on `feat/cursed-spirits`: `c0d4521` (frozen API), `2f2a45d` (P0 ctor fix),
`69d0176` (lazy goal stats + full spawn gate with Block 4 cap conjunct).

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
  modified `src/gametest/resources/fabric.mod.json` (appended `CursedSpiritGameTests`)

## Acceptance (Факт lines)
- Факт: `gradlew.bat compileJava compileClientJava compileTestJava compileGametestJava` → BUILD SUCCESSFUL (11–12 s).
- Факт: `gradlew.bat test --tests "jujutsu.mod.cursedspirit.*" --tests "jujutsu.mod.combat.*"` →
  BUILD SUCCESSFUL; XML: ProfileTest 5/5, VariantTest 7/7, AttackPolicyTest 7/7, TagContractTest 2/2,
  RegistryTest 4/4, TargetResolverTest 2/2, ResourceContractTest 5/5, CombatStaggerClearTest 2/2 — 0 failures.
- Факт: `runGameTest` lane `build/test-results/gametest/junit.xml` → 79 cases, my 8 scenarios GREEN
  (R11 two-sources, R12 stagger window, R13 knockback velocity+displacement, R14 lesser+common swap,
  R15 greater deny, R25 lethal removal, tags+variant sync, follow-range pins); all pre-existing
  Todo/Nobara/Megumi scenarios GREEN (failures in every lane run were only mine).
- Факт: R1 registry contract green — `CursedSpiritRegistryTest`: 3 ids in `BuiltInRegistries.ENTITY_TYPE`,
  category MONSTER ×3, hitboxes 0.85×1.0 / 0.75×1.9 / 1.35×2.4, `canSerialize()` true ×3
  (`JujutsuEntities.java:129-140`, no `.noSave()`).
- Факт: R20 linkage green — `mutatedProfileRowMovesDerivedDamage`: test-side mutated row moves
  `primaryDamage`/`aoeDamage` (profile read, not literals).
- Факт: R16 green — `CursedSpiritTargetResolverTest` through `resolveForTests` (far pierced beats near graze).
- Факт: live probe crash (Main's report) fixed — `defineSynchedData` is now tier-independent
  (`builder.define(DATA_VARIANT, 0)`, `CursedSpiritEntity.java:166-175`); all lane spawns construct.

## Red-proof records (mutation → observed failure → restored)
1. `CursedSpiritProfile` COMMON weight 20→100 → `spawnWeightsOrder…` FAILED
   ("LESSER weight 65 must exceed COMMON 100") → restored, green.
2. `aoeMemberIndices` `i == primaryIndex` → `i != primaryIndex` → `aoeMembershipKeeps…` FAILED
   ("expected: <[1]> but was: <[0]>") → restored, green.
3. `boogie_woogie_immune.json` emptied → `boogieWoogieImmuneTagContainsOnlyGreater` FAILED
   ("greater immune: []") → restored, green.
4. P0 live red-proof (from Main's probe, pre-fix): `/summon lesser_cursed_spirit` →
   `IndexOutOfBoundsException` at `defaultVariantOrdinal` (super-ctor runs before `tier` assignment).
   Fixed (`define(DATA_VARIANT, 0)` + null-safe `variant()` + lazy goal stats); lane spawns all 3 types.
5. Lane red-proofs per GameTest scenario: NOT RUN (lane time + victim blocker below). Owed as follow-up.

## Risks / unresolved
- **U1 (blocks R3/R4/R5/R24 verification): mock players cannot leave CREATIVE.** Evidence:
  `setGameMode(SURVIVAL)` returns true but `gameMode()` stays CREATIVE (lane message:
  `[before=CREATIVE setGameMode->true after=CREATIVE]`); `Player.isCreative()` reads `gameMode()`
  (javap-verified on 1.21.8 named jar); `NearestAttackableTargetGoal<Player>` never acquires a
  creative victim (`getTarget()==null` at tick 8, HP unchanged at deadlines — 4 scenarios red).
  The 4 scenarios keep the STRICT premise (no loosening) and fail honestly. Fix direction (not tried):
  construct the victim directly (`new ServerPlayer(server, level, profile, ClientInformation…)`,
  Nobara-test pattern) or flip via `ServerPlayerGameMode`; needs a lane cycle each.
- **U2 (blocks the whole lane, NOT my file): `CursedSpiritIntegrationGameTests.java:231`
  `boolean damaged = caster.attack(spirit)` — `Player.attack(Entity)` returns void in 1.21, so
  `compileGametestJava` fails and `runGameTest` cannot execute at all. Suggested fix for Main:
  `caster.attack(spirit);` then assert the HP delta. My files are unaffected.
- **Relay, not mine:** `VesselBoundaryTest` will fail on Block 3's `client.render.cursedspirit`
  package (not a registered vessel id) — needs a package move or test fix by Main/Block 3.
- **Relay to Block 4:** javap on the pinned 1.21.8 jar shows `Mob.checkSpawnRules` is a bare
  `return true` and `Monster.checkMonsterSpawnRules` is static (SpawnPlacements-only) — so plan:255
  is inaccurate, my explicit PEACEFUL gate is load-bearing, and NOTHING gates light: Block 4's
  lit-cell→false assert cannot pass without an explicit light check in their conjunct.
- R13 note: strict `LESSER>COMMON` displacement is impossible (frozen KB-res 0.0/0.0) AND exact
  equality is false in integration (observed 1.075 vs 1.125); oracle pins equal impulse response
  (|Δv| < 0.05) + closeness (|Δd| ≤ 0.15) + strict `common > greater`. Deviation documented, not silent.
- NBT round-trip: `resolveLoadedVariant` unit-tested (unknown/cross-tier re-roll, valid survives);
  no full save/load cycle in-world (no cheap `ValueInput` harness) — in-world `setVariant` sync covered.

## Scope-Growth
None triggered. Cross-block requests served inside my row: checkSpawnRules with PEACEFUL gate +
  Block 4 `belowLocalCap` conjunct (full variant, compiles against landed Block 4 classes);
  7-arg `diagnostic` overload on my fixture for Block 5's call sites (additive).
