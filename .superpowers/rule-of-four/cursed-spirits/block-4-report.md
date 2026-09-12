# Block 4 report — natural spawn integration + local cap

## What was done

Created Block 4's four owned files (plan Task 4, Steps 1–5):

- `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritSpawnRules.java` — pure `belowCap(int, int)`
  (`nearby < cap`) + `belowLocalCap(LevelAccessor, BlockPos)` adapter (ServerLevel → count
  `CursedSpiritEntity` in `AABB(pos).inflate(CROWD_RADIUS)` vs `MAX_SPIRITS_NEARBY`; non-server → true).
- `src/main/java/jujutsu/mod/cursedspirit/CursedSpiritSpawnIntegration.java` — `spawnRows()` (three
  rows, every number from `CursedSpiritProfile`) + `register()` (three
  `BiomeModifications.addSpawn(foundInOverworld(), MONSTER, …)` calls) + the D3 comment (why
  `SpawnPlacements` is untouched).
- `src/test/java/jujutsu/mod/cursedspirit/CursedSpiritSpawnRulesTest.java` — 5 JUnit tests (cap
  boundaries, weight ordering, group sanity, rows-from-profile, rows-target-tier-types).
- `src/gametest/java/jujutsu/mod/gametest/CursedSpiritSpawnGameTests.java` — one scenario,
  `spawnGateRefusesLightPeacefulAndCrowdButAllowsDark`: sealed dark room (opaque shell) vs
  glowstone-lit cell, PEACEFUL phase (set → assert → restore synchronously), cap phase at exactly
  `MAX_SPIRITS_NEARBY`, premise + sweep counts, full discard on all paths.

Plus the two serialized edits below (applied after `block-2 report filed` arrived). Nothing else
touched — `CursedSpiritEntity.java`, Block 2/5 test files were never edited.

## Acceptance evidence (`Факт:` lines)

- Факт: `gradlew.bat test --tests "jujutsu.mod.cursedspirit.CursedSpiritSpawnRulesTest"` → BUILD
  SUCCESSFUL, `TEST-jujutsu.mod.cursedspirit.CursedSpiritSpawnRulesTest.xml`: tests=5 failures=0
  errors=0 skipped=0.
- Факт: `gradlew.bat compileJava compileTestJava compileGametestJava` → BUILD SUCCESSFUL; class
  files present for all four new files (`CursedSpiritSpawnRules`, `CursedSpiritSpawnIntegration`
  (+`$SpawnRow`), `CursedSpiritSpawnRulesTest`, `CursedSpiritSpawnGameTests`).
- Факт (JUnit red-proof): `belowCap` mutated `<` → `>=` → `belowCapBoundary()` FAILED
  (`AssertionFailedError: empty area passes ==> expected: <true> but was: <false>`, suite 5 tests /
  1 failure) → restored `<` → green 5/5 again.
- Факт (javap on the 1.21.8 merged jar, full bodies re-dumped): `Mob.checkSpawnRules` = bare
  `return true`; `PathfinderMob.checkSpawnRules` = `getWalkTargetValue(blockPosition) >= 0`
  (`fcmpl; iflt`, so equality passes); `Monster.getWalkTargetValue` =
  `-getPathfindingCostFromLightLevels`; cost = `magic - 0.5`; `magic` = `Mth.lerp(ambient, g, 1)`
  with `g = f / (4 - 3f)`, `f = brightness / 15` — overworld ambient is 0, so `magic = g`
  regardless of lerp arg order. Pass ⟺ `g ≤ 0.5` ⟺ `f ≤ 0.8` ⟺ **local brightness ≤ 12**
  (12 gives exactly 0.0 → passes; 13 gives −0.12 → refuses). Earlier report revisions said ≤ 7 —
  that was wrong; ≤ 12 is the proven bound. Also verified: `Monster.shouldDespawnInPeaceful` =
  true; static `SpawnPlacements.checkSpawnRules` on an unregistered type = true;
  `SpawnPlacements.register` is private; `NaturalSpawner` calls static
  `isSpawnPositionOk`/`checkSpawnRules` (×2 sites) AND the instance `Mob.checkSpawnRules` (×2 sites);
  `LevelAccessor.getDifficulty()` exists;
  `MinecraftServer.setDifficulty(Difficulty, boolean)` exists; `GameTestHelper.spawn /
  assertFalse / setDayTime` exist. `BiomeModifications.addSpawn(selector, group, type, weight,
  min, max)` signature confirmed from fabric-biome-api-v1 16.1.0 sources.
- Факт (gates applied): `CursedSpirits.registerServerHooks()` +=
  `CursedSpiritSpawnIntegration.register();` (init order safe: `JujutsuEntities.register()` runs
  first in `JujutsuMod.onInitialize`); `fabric.mod.json` +=
  `jujutsu.mod.gametest.CursedSpiritSpawnGameTests` after the `CursedSpiritGameTests` row.
  ServerCore's `d723f31` already contains the `&& belowLocalCap` conjunct in
  `CursedSpiritEntity.checkSpawnRules` — no routing left open.
- Факт (judgement-proof hardening): the cap assert first targeted the last crowd body at open-sky
  crowdSpot(9)=(7,1,6), where daylight alone refuses — a broken cap would hide behind the light
  half. Moved the assert to the in-room `darkProbe` (super pinned true at any sky), so it observes
  ONLY the cap half.
- Факт (GameTest red-proof, cap): `belowCap` `<` → `<=` → lane FAILS exactly at `natural check on
  the in-room probe at cap (10 nearby): expected <false>` (all earlier phases green, incl. dark at
  count 3) → restored `<`.
- Факт (final green, `build/test-results/gametest/junit.xml` after restore): lane total=80,
  `cursed_spirit_spawn_game_tests_spawn_gate_refuses_light_peaceful_and_crowd_but_allows_dark`
  → PASS on a fresh random offset (all four phases green with the darkProbe-pinned cap assert).
  Same lane: 6 failures, all in Block 2's `CursedSpiritGameTests` (their combat/victim refactor in
  flight — their files, not touched).
- Факт (GameTest red-proof, coarse): `belowCap` `<` → `>=` → lane FAILS at `natural check in the
  dark room: expected <true>` (counts below cap now refuse) → restored.

## Plan-text correction (accepted by Main, folded into D3/Step 1 per Main)

Plan Step 1 said "`super` = `Monster.checkMonsterSpawnRules`". The bytecode says otherwise: `super`
from the entity resolves to **`PathfinderMob.checkSpawnRules`** (light walk-value gate,
**local brightness ≤ 12 passes** — full derivation in the javap Факт above; earlier revisions of
this report said ≤ 7, which was wrong) — `Monster` overrides only `getWalkTargetValue`, and the
static `checkMonsterSpawnRules` (difficulty + the stricter vanilla darkness roll) never runs for an
unregistered type. Consequence: our gate is laxer than vanilla darkness (allows local light 8–12);
a strictly vanilla-dark gate would need an explicit conjunct (Block 5/Main decision, not this
block). Observable effect is still lit→false (glowstone ≈ 14) / dark→true (sealed room = 0), but
**difficulty is NOT gated by `super`** —

## Cross-block coordination (via Main)

- Sent Main the override snippet for `CursedSpiritEntity.java` (Block 2's file), then confirmed the
  PEACEFUL-augmented shape with imports (`Difficulty`, `LevelAccessor`). ServerCore's `d723f31`
  landed the override WITH the PEACEFUL gate AND the `&& belowLocalCap` conjunct — no routing left.
- Notified ServerCore of a dropped `finalizeSpawn` closing brace during their insert (35-error
  break, blocked all compilation incl. my JUnit); they restored it. Sibling `CursedSpiritGameTests`
  / `CursedSpiritTestFixtures` breakage also resolved by them; final `compileGametestJava` green
  includes my GameTest class.

## Serialized edits (APPLIED this session, after `block-2 report filed` arrived)

1. `CursedSpirits.registerServerHooks()` += `CursedSpiritSpawnIntegration.register();`.
2. `fabric.mod.json` += `jujutsu.mod.gametest.CursedSpiritSpawnGameTests` after the
   `CursedSpiritGameTests` row.

## Expected night-share estimate (assumptions stated)

Profile weights 65/20/5 (groups 2–4 / 1–2 / 1–1, means 3 / 1.5 / 1). Against a plains-like vanilla
night MONSTER pool (~600 by weight — varies by biome; mobcap/despawn/sim-distance excluded):
cursed packs ≈ 90/(600+90) ≈ **13% of monster spawn events** (~1 in 8 packs); within cursed packs
by weight lesser:common:greater ≈ **72% : 22% : 6%**; by bodies ≈ **85% : 13% : 2%** (195:30:5).
Feel: roughly one lesser pack every few vanilla packs; a greater roughly once in fifty packs.

## Explicit decisions

- No daylight burning: plain `Monster`; they persist once spawned, spawn where local brightness
  ≤ 12 (super's walk-value gate — laxer than vanilla's 0–7 darkness roll, see correction above) —
  sun behavior like spiders, not zombies.
- Peaceful = no spawns: explicit `PEACEFUL → false` gate in the override (Main's requirement) +
  vanilla despawn backstop. Difficulty gating otherwise mirrors vanilla monsters for light; no
  custom spawner, no mobcap/despawn changes.
- No `SpawnPlacements`, no access widener, no mixin (D3). Out of scope kept out: spawn eggs,
  commands, nether/end, structure spawns.
- GameTest addition beyond the letter of Step 5: a PEACEFUL phase (pins Main's routed requirement;
  set→assert→restore inside one callback — no tick passes, siblings cannot observe it).
- Unit-test bootstrap: `SharedConstants/Bootstrap` in `@BeforeAll` but deliberately NO
  `JujutsuEntities.register()` call (the registry test owns registration; a second register of the
  same ids would collide in one JVM).

## Risks / unresolved

- R1 (closed): final green lane confirmation recorded above. Remaining lane red is entirely Block
  2's class (6 fails, victim/combat premises under their active refactor).
- R2 (nit, not mine to edit): ServerCore's comment in `CursedSpiritEntity.checkSpawnRules` says
  "any light gating belongs in Block 4's conjunct" — inaccurate (light comes from
  `super`/PathfinderMob); flagged here for Main.
- R3: my premise/sweep guards assume sibling tests leave no live spirits within ~56 blocks of my
  structure (I clear at tick 1 + assert premise) — a red premise names contamination, not a logic
  failure.
- No Scope-Growth triggered. Committed as `15f0826 feat(cursed-spirits): natural overworld
  spawning` (4 files staged by explicit path; siblings' working-tree changes untouched, nothing
  pushed).
