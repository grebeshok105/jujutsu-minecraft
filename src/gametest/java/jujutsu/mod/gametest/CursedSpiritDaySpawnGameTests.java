package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritSpawnRules;
import jujutsu.mod.cursedspirit.CursedSpiritSpawnSchedule;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Day/night spawn-gate oracles (Block 4, Step 2): the Block 4 day branch of
 * {@code CursedSpiritEntity.checkSpawnRules}, which <i>weakens</i> the vanilla light conjunct by
 * day and never touches it at night.
 *
 * <p>Clock discipline: the GameTest level is shared, so every oracle sets its own time in the
 * same callback that asserts — {@code checkSpawnRules} reads the clock synchronously, which
 * makes each oracle ordering-proof. The only long-window scenario here is R43, and it writes no
 * time at all (targeting is time-independent).
 *
 * <p>Owner-scoped cleanup like the sibling spawn-gate class: every probe is tracked and
 * discarded on all paths, the day-chance pin is always restored in a {@code finally}, and the
 * difficulty flip is restored likewise.
 */
public final class CursedSpiritDaySpawnGameTests {

	// No explicit constructor: the fabric loader instantiates entrypoint classes reflectively,
	// so the implicit public no-arg constructor is required.

	private static final BlockPos DARK_FEET = new BlockPos(2, 1, 2);
	private static final BlockPos LIT_FEET = new BlockPos(6, 1, 2);
	private static final BlockPos LIT_LAMP = new BlockPos(6, 1, 3);

	private static final long NOON = 6000L;
	private static final long MIDNIGHT = 18000L;

	private static final int ORACLE_TICK = 15;
	private static final int SWEEP_TICK = 30;

	private final List<CursedSpiritEntity> owned = new ArrayList<>();

	/** Day and night both allow in the sealed dark room (night needs no roll, day passes super). */
	@GameTest(maxTicks = 60)
	public void darkAllowsByDayAndByNight(GameTestHelper helper) {
		String fixture = "darkAllowsByDayAndByNight";
		buildDarkRoom(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			try {
				CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
				level.setDayTime(NOON);
				CursedSpiritEntity dayProbe = spawnProbe(helper, owned, DARK_FEET);
				helper.assertTrue(dayProbe.checkSpawnRules(level, EntitySpawnReason.SPAWNER),
						GameTestFixtures.diagnostic(fixture, helper.getTick(), "day check in the dark room",
								"true", "see report"));
				level.setDayTime(MIDNIGHT);
				CursedSpiritEntity nightProbe = spawnProbe(helper, owned, DARK_FEET);
				helper.assertTrue(nightProbe.checkSpawnRules(level, EntitySpawnReason.SPAWNER),
						GameTestFixtures.diagnostic(fixture, helper.getTick(), "night check in the dark room",
								"true", "see report"));
			} finally {
				for (CursedSpiritEntity spirit : owned) {
					spirit.discard();
				}
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> {
			helper.assertTrue(countLiveOwned() == 0, GameTestFixtures.diagnostic(fixture, helper.getTick(),
					"sweep: owned probes discarded", "0", countLiveOwned()));
			helper.succeed();
		});
	}

	/**
	 * The day branch follows the pinned chance in a lit cell: pin 1.0 allows where vanilla
	 * refuses, pin 0.0 refuses. Probed with {@link EntitySpawnReason#NATURAL}: post-merge the
	 * day roll (and crowd cap) apply to NATURAL/CHUNK_GENERATION only — a SPAWNER probe takes
	 * the bare vanilla gate and never reaches the roll. The probe is hoisted 80 blocks up:
	 * open sky at noon keeps the vanilla light half refused, while the crowd cap's 48-block
	 * box no longer reaches sibling arenas' spirits on the shared level (observed: 23 nearby
	 * bodies from concurrent scenarios refused the gate before the roll was ever reached).
	 * Red-proof: deleting the day branch reddens the pin-1.0 assert.
	 */
	@GameTest(maxTicks = 60)
	public void dayLitFollowsPinnedChance(GameTestHelper helper) {
		String fixture = "dayLitFollowsPinnedChance";
		buildDarkRoom(helper);
		helper.setBlock(LIT_FEET.below(), Blocks.STONE);
		helper.setBlock(LIT_LAMP, Blocks.GLOWSTONE);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			try {
				CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
				level.setDayTime(NOON);
				CursedSpiritSpawnSchedule.pinDayChance(1.0);
				try {
					CursedSpiritEntity allowed = spawnProbe(helper, owned, LIT_FEET);
					hoistAboveCrowd(helper, allowed);
					helper.assertTrue(allowed.checkSpawnRules(level, EntitySpawnReason.NATURAL),
							GameTestFixtures.diagnostic(fixture, helper.getTick(),
									"day lit check with chance pinned to 1.0", "true",
									"see report " + capDiag(level, allowed.blockPosition())));
				} finally {
					CursedSpiritSpawnSchedule.resetDayChance();
				}
				CursedSpiritSpawnSchedule.pinDayChance(0.0);
				try {
					CursedSpiritEntity refused = spawnProbe(helper, owned, LIT_FEET);
					hoistAboveCrowd(helper, refused);
					helper.assertFalse(refused.checkSpawnRules(level, EntitySpawnReason.NATURAL),
							GameTestFixtures.diagnostic(fixture, helper.getTick(),
									"day lit check with chance pinned to 0.0", "false", "see report"));
				} finally {
					CursedSpiritSpawnSchedule.resetDayChance();
				}
			} finally {
				CursedSpiritSpawnSchedule.resetDayChance();
				for (CursedSpiritEntity spirit : owned) {
					spirit.discard();
				}
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> {
			helper.assertTrue(countLiveOwned() == 0, GameTestFixtures.diagnostic(fixture, helper.getTick(),
					"sweep: owned probes discarded", "0", countLiveOwned()));
			helper.assertTrue(CursedSpiritSpawnSchedule.dayChance() == CursedSpiritSpawnSchedule.DAY_SPAWN_CHANCE,
					GameTestFixtures.diagnostic(fixture, helper.getTick(), "sweep: day-chance pin released",
							"BALANCE constant", CursedSpiritSpawnSchedule.dayChance()));
			helper.succeed();
		});
	}
	@GameTest(maxTicks = 60)
	public void peacefulRefusesDaySpawns(GameTestHelper helper) {
		String fixture = "peacefulRefusesDaySpawns";
		buildDarkRoom(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			// A peaceful day refuses the spawn because of the difficulty gate alone. Asserted on the
			// pure predicate instead of flipping the shared level to PEACEFUL: a global window makes
			// Mob.checkDespawn discard sibling arenas' Monster bodies while it is open.
			level.setDayTime(NOON);
			helper.assertFalse(CursedSpiritSpawnRules.difficultyAllows(Difficulty.PEACEFUL),
					GameTestFixtures.diagnostic(fixture, helper.getTick(), "peaceful day gate predicate",
							"false", "see report"));
		});
		helper.runAtTickTime(SWEEP_TICK, () -> {
			helper.assertTrue(countLiveOwned() == 0, GameTestFixtures.diagnostic(fixture, helper.getTick(),
					"sweep: owned probes discarded", "0", countLiveOwned()));
			helper.succeed();
		});
	}

	/**
	 * R37: the day spawn is not limited to one tier — N = 100 gate evaluations per tier in a lit
	 * cell by day with the chance pinned to 1.0. The probe reason is NATURAL because post-merge
	 * the day roll lives on the NATURAL/CHUNK_GENERATION branch only (a SPAWNER probe would take
	 * the bare vanilla gate and observe nothing). The crowd cap that the natural branch adds is
	 * far away here: arenas hold a handful of bodies against a cap of 10.
	 */
	@GameTest(maxTicks = 60)
	public void daySpawnCoversAllTiers(GameTestHelper helper) {
		String fixture = "daySpawnCoversAllTiers";
		buildDarkRoom(helper);
		helper.setBlock(LIT_FEET.below(), Blocks.STONE);
		helper.setBlock(LIT_LAMP, Blocks.GLOWSTONE);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			level.setDayTime(NOON);
			CursedSpiritSpawnSchedule.pinDayChance(1.0);
			try {
				assertTierPassesByDay(helper, fixture, level, JujutsuEntities.LESSER_CURSED_SPIRIT, "lesser");
				assertTierPassesByDay(helper, fixture, level, JujutsuEntities.CURSED_SPIRIT, "common");
				assertTierPassesByDay(helper, fixture, level, JujutsuEntities.GREATER_CURSED_SPIRIT, "greater");
			} finally {
				CursedSpiritSpawnSchedule.resetDayChance();
				for (CursedSpiritEntity spirit : owned) {
					spirit.discard();
				}
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> {
			helper.assertTrue(countLiveOwned() == 0, GameTestFixtures.diagnostic(fixture, helper.getTick(),
					"sweep: owned probes discarded", "0", countLiveOwned()));
			helper.succeed();
		});
	}

	/**
	 * R43 scenario (gate owned by Block 1, checked here): a victim with no vessel selected — a
	 * non-perceiver once Block 1 lands — is not acquired as a target by day.
	 *
	 * <p><b>Blocked on Block 1:</b> the target predicate without the perception gate acquires
	 * any survival player, so this scenario is expected RED until Block 1's aggro gate lands;
	 * Main runs it at the barrier, not before.
	 */
	@GameTest(maxTicks = 240)
	public void nonPerceiverIsNotTargetByDay(GameTestHelper helper) {
		String fixture = "nonPerceiverIsNotTargetByDay";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		helper.runAtTickTime(5, () -> {
			owned.clear();
			r43Observed = false;
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			helper.getLevel().setDayTime(NOON);
			net.minecraft.server.level.ServerPlayer victim =
					CursedSpiritTestFixtures.setupVictim(helper, fixture, new BlockPos(4, 1, 2));
			victims.add(victim);
			CursedSpiritEntity spirit = CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
					JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2));
			owned.add(spirit);
		});
		// Liveness repair: a concurrent global PEACEFUL flip (the peaceful oracles in this
		// lane) discards Monster bodies via Mob.checkDespawn regardless of persistence. A
		// mid-window flip must not fake the oracle either way, so a lost body is replaced
		// well before the oracle — the null-target assert itself is untouched.
		helper.runAtTickTime(150, () -> {
			helper.getLevel().setDayTime(NOON);
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			if (!isLiveSpirit()) {
				respawnR43Spirit(helper, fixture);
			}
		});
		helper.runAtTickTime(200, () -> {
			helper.getLevel().setDayTime(NOON);
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			if (!isLiveSpirit()) {
				respawnR43Spirit(helper, fixture);
				return;
			}
			CursedSpiritEntity spirit = owned.get(0);
			helper.assertTrue(spirit.getTarget() == null, GameTestFixtures.diagnostic(fixture,
					helper.getTick(), "R43: non-perceiver not targeted by day", "null",
					spirit.getTarget()));
			r43Observed = true;
		});
		// Fallback oracle for a flip landing between the repair and tick 200: same asserts,
		// same live-body premise, only later.
		helper.runAtTickTime(210, () -> {
			if (r43Observed) {
				return;
			}
			helper.getLevel().setDayTime(NOON);
			CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
			CursedSpiritEntity spirit = owned.isEmpty() ? null : owned.get(0);
			helper.assertTrue(isLiveSpirit(), GameTestFixtures.diagnostic(fixture,
					helper.getTick(), "premise: spirit alive at the oracle", "alive", spirit));
			helper.assertTrue(spirit.getTarget() == null, GameTestFixtures.diagnostic(fixture,
					helper.getTick(), "R43: non-perceiver not targeted by day", "null",
					spirit.getTarget()));
			r43Observed = true;
		});
		helper.runAtTickTime(220, () -> {
			for (CursedSpiritEntity spirit : owned) {
				spirit.discard();
			}
			for (net.minecraft.server.level.ServerPlayer victim : victims) {
				CursedSpiritTestFixtures.cleanupVictim(helper, victim);
			}
			victims.clear();
			if (!r43Observed) {
				helper.fail(GameTestFixtures.diagnostic(fixture, helper.getTick(),
						"R43 oracle never observed a live body", "observed", "not observed"));
			}
			helper.succeed();
		});
	}

	private boolean isLiveSpirit() {
		if (owned.isEmpty()) {
			return false;
		}
		CursedSpiritEntity spirit = owned.get(0);
		return spirit != null && !spirit.isRemoved() && spirit.isAlive();
	}

	private void respawnR43Spirit(GameTestHelper helper, String fixture) {
		for (CursedSpiritEntity old : owned) {
			try {
				old.discard();
			} catch (RuntimeException ignored) {
				// Best-effort: the old body may already be removed.
			}
		}
		owned.clear();
		owned.add(CursedSpiritTestFixtures.spawnSpirit(helper, fixture,
				JujutsuEntities.LESSER_CURSED_SPIRIT, new BlockPos(2, 1, 2)));
	}

	private final List<net.minecraft.server.level.ServerPlayer> victims = new ArrayList<>();
	private boolean r43Observed;

	private void assertTierPassesByDay(GameTestHelper helper, String fixture, ServerLevel level,
			EntityType<CursedSpiritEntity> type, String tierName) {
		CursedSpiritEntity probe = helper.spawn(type, LIT_FEET);
		probe.setPersistenceRequired();
		owned.add(probe);
		hoistAboveCrowd(helper, probe);
		try {
			for (int i = 0; i < 100; i++) {
				if (!probe.checkSpawnRules(level, EntitySpawnReason.NATURAL)) {
					helper.assertTrue(false, GameTestFixtures.diagnostic(fixture, helper.getTick(),
							"day lit gate for " + tierName + " (N=100)", "true on all 100",
							"false at roll " + i + " " + capDiag(level, probe.blockPosition())));
					return;
				}
			}
		} finally {
			probe.discard();
		}
	}

	private static void buildDarkRoom(GameTestHelper helper) {
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
			}
		}
		for (int y = 1; y <= 2; y++) {
			for (int x = 1; x <= 3; x++) {
				helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
				helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
			}
			helper.setBlock(new BlockPos(1, y, 2), Blocks.STONE);
			helper.setBlock(new BlockPos(3, y, 2), Blocks.STONE);
		}
	}

	/**
	 * Lifts the probe well above the shared level's spirit arenas: 80 blocks puts ground
	 * bodies outside the crowd cap's 48-block box, while open sky at noon still refuses the
	 * vanilla light half of {@code checkSpawnRules} — so the NATURAL gate outcome is decided
	 * by the day roll alone, deterministically.
	 */
	private static void hoistAboveCrowd(GameTestHelper helper, CursedSpiritEntity probe) {
		BlockPos abs = helper.absolutePos(LIT_FEET).above(80);
		probe.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
	}

	/** Ambient-crowd diagnostic: which conjunct of the natural branch refuses, if any. */
	private static String capDiag(ServerLevel level, BlockPos pos) {
		int nearby = level.getEntitiesOfClass(CursedSpiritEntity.class,
				new net.minecraft.world.phys.AABB(pos)
						.inflate(jujutsu.mod.cursedspirit.CursedSpiritProfile.CROWD_RADIUS)).size();
		return "[nearby=" + nearby + "/" + jujutsu.mod.cursedspirit.CursedSpiritProfile.MAX_SPIRITS_NEARBY
				+ " belowCap=" + jujutsu.mod.cursedspirit.CursedSpiritSpawnRules.belowLocalCap(level, pos)
				+ " day=" + CursedSpiritSpawnSchedule.isDaytime(level.dayTime())
				+ " chance=" + CursedSpiritSpawnSchedule.dayChance() + "]";
	}

	private static CursedSpiritEntity spawnProbe(GameTestHelper helper, List<CursedSpiritEntity> live,
			BlockPos relativePos) {
		CursedSpiritEntity spirit = helper.spawn(JujutsuEntities.LESSER_CURSED_SPIRIT, relativePos);
		live.add(spirit);
		return spirit;
	}

	private int countLiveOwned() {
		int live = 0;
		for (CursedSpiritEntity spirit : owned) {
			if (!spirit.isRemoved()) {
				live++;
			}
		}
		return live;
	}
}
