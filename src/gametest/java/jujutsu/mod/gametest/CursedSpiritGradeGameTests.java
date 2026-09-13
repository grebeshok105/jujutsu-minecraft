package jujutsu.mod.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.CursedSpiritGradeStats;
import jujutsu.mod.cursedspirit.CursedSpiritRollPolicy;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Grade-axis world oracles (Block 2, Step 7): valid grades, finals in bands,
 * no distance gradient, reload round-trip, finals invariant on live spawns.
 * No victims: grade needs no perception. Every scenario finalizes its bodies
 * with COMMAND (the summon path) because {@code helper.spawn} skips it.
 */
public final class CursedSpiritGradeGameTests {
	private static final int ORACLE_TICK = 15;
	private static final int SWEEP_TICK = 30;
	private static final int SAMPLE = 120;
	private static final int PROBE_N = 20;

	private final List<CursedSpiritEntity> owned = new ArrayList<>();

	/** All rolled grades valid, all three seen, finals disjoint per stat. */
	@GameTest(maxTicks = 60)
	public void spawnRollsValidGradesWithDisjointFinals(GameTestHelper helper) {
		String fixture = "spawnRollsValidGradesWithDisjointFinals";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			owned.clear();
			try {
				Map<CursedSpiritGrade, double[]> extremes = sampleGrades(helper, SAMPLE);
				for (CursedSpiritGrade grade : CursedSpiritGrade.SPAWNABLE_V1) {
					helper.assertTrue(extremes.containsKey(grade), diag(fixture, helper, "grade seen", grade));
				}
				assertDisjoint(helper, fixture, extremes, true, false, false, "HP");
				assertDisjoint(helper, fixture, extremes, false, true, false, "damage");
				assertDisjoint(helper, fixture, extremes, false, false, true, "speed");
			} finally {
				discardOwned();
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> helper.succeed());
	}

	/** Applied stats equal the rolled finals and sit in the grade band. */
	@GameTest(maxTicks = 60)
	public void finalStatsMatchRolledAndSitInBands(GameTestHelper helper) {
		String fixture = "finalStatsMatchRolledAndSitInBands";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			owned.clear();
			try {
				for (int i = 0; i < PROBE_N; i++) {
					CursedSpiritEntity spirit = finalizeOne(helper, i);
					CursedSpiritGradeStats stats = spirit.gradeStats();
					helper.assertTrue(stats.inBandsOf(spirit.grade()), diag(fixture, helper, "in band", stats));
					// getAttributeValue, not getMaxHealth: the latter narrows to float, so an
					// exact double assert would fail on vanilla's own rounding, not on the roll.
					helper.assertTrue(spirit.getAttributeValue(Attributes.MAX_HEALTH) == stats.maxHealth(),
							diag(fixture, helper, "maxHealth applied", stats.maxHealth()));
					helper.assertTrue(spirit.getAttributeValue(Attributes.ATTACK_DAMAGE) == stats.attackDamage(),
							diag(fixture, helper, "damage applied", stats.attackDamage()));
					helper.assertTrue(spirit.getAttributeValue(Attributes.MOVEMENT_SPEED) == stats.movementSpeed(),
							diag(fixture, helper, "speed applied", stats.movementSpeed()));
				}
			} finally {
				discardOwned();
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> helper.succeed());
	}

	/**
	 * Position never changes a newborn roll: bodies finalized in both arena halves exactly
	 * replay their grade, variant and stats from the stored seed. Unlike comparing two random
	 * samples, this oracle is deterministic and catches any positional post-processing.
	 */
	@GameTest(maxTicks = 120)
	public void noDistanceGradientAcrossHalves(GameTestHelper helper) {
		String fixture = "noDistanceGradientAcrossHalves";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			owned.clear();
			try {
				for (int i = 0; i < 18; i++) {
					int x = i % 2 == 0 ? 1 + (i % 3) : 4 + (i % 3);
					CursedSpiritEntity spirit = spawnAt(helper,
							new BlockPos(x, 1, 1 + (i / 3) % 6), i);
					CursedSpiritRollPolicy.Newborn replay = CursedSpiritRollPolicy.rollNewborn(
							RandomSource.create(spirit.rollSeed()), spirit.tier());
					helper.assertTrue(replay.grade() == spirit.grade(),
							diag(fixture, helper, "grade is seed-only", replay.grade() + "/" + spirit.grade()));
					helper.assertTrue(replay.variant() == spirit.variant(),
							diag(fixture, helper, "variant is seed-only", replay.variant() + "/" + spirit.variant()));
					helper.assertTrue(replay.stats().equals(spirit.gradeStats()),
							diag(fixture, helper, "stats are seed-only", replay.stats() + "/" + spirit.gradeStats()));
				}
			} finally {
				discardOwned();
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> helper.succeed());
	}

	/** Save/load round-trip keeps grade, stats, variant and seed (R34). */
	@GameTest(maxTicks = 60)
	public void reloadKeepsGradeAndAttributes(GameTestHelper helper) {
		String fixture = "reloadKeepsGradeAndAttributes";
		CursedSpiritTestFixtures.layStoneFloor(helper);
		CursedSpiritTestFixtures.ensureHostileDifficulty(helper);
		helper.runAtTickTime(ORACLE_TICK, () -> {
			ServerLevel level = helper.getLevel();
			owned.clear();
			try {
				for (int i = 0; i < 5; i++) {
					CursedSpiritEntity before = finalizeOne(helper, i);
					TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
					before.saveWithoutId(output);
					CompoundTag tag = output.buildResult();
					EntityType<CursedSpiritEntity> type = typeOf(before);
					before.discard();
					owned.remove(before);
					CursedSpiritEntity after = type.create(level, EntitySpawnReason.LOAD);
					helper.assertTrue(after != null, diag(fixture, helper, "recreated", type));
					after.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
					after.setPersistenceRequired();
					owned.add(after);
					helper.assertTrue(after.grade() == before.grade(), diag(fixture, helper, "grade kept", before.grade()));
					helper.assertTrue(after.gradeStats().equals(before.gradeStats()),
							diag(fixture, helper, "stats kept", before.gradeStats()));
					helper.assertTrue(after.variant() == before.variant(), diag(fixture, helper, "variant kept", before.variant()));
					helper.assertTrue(after.rollSeed() == before.rollSeed(), diag(fixture, helper, "seed kept", before.rollSeed()));
					helper.assertTrue(after.getMaxHealth() == before.getMaxHealth(),
							diag(fixture, helper, "maxHealth kept", before.getMaxHealth()));
				}
			} finally {
				discardOwned();
			}
		});
		helper.runAtTickTime(SWEEP_TICK, () -> helper.succeed());
	}

	private Map<CursedSpiritGrade, double[]> sampleGrades(GameTestHelper helper, int count) {
		Map<CursedSpiritGrade, List<Double>> hp = new EnumMap<>(CursedSpiritGrade.class);
		Map<CursedSpiritGrade, List<Double>> dmg = new EnumMap<>(CursedSpiritGrade.class);
		Map<CursedSpiritGrade, List<Double>> spd = new EnumMap<>(CursedSpiritGrade.class);
		for (int i = 0; i < count; i++) {
			CursedSpiritEntity spirit = finalizeOne(helper, i);
			helper.assertTrue(CursedSpiritGrade.SPAWNABLE_V1.contains(spirit.grade()),
					diag("sample", helper, "grade spawnable", spirit.grade()));
			CursedSpiritGradeStats stats = spirit.gradeStats();
			helper.assertTrue(stats.inBandsOf(spirit.grade()), diag("sample", helper, "final in band", stats));
			hp.computeIfAbsent(spirit.grade(), g -> new ArrayList<>()).add(stats.maxHealth());
			dmg.computeIfAbsent(spirit.grade(), g -> new ArrayList<>()).add(stats.attackDamage());
			spd.computeIfAbsent(spirit.grade(), g -> new ArrayList<>()).add(stats.movementSpeed());
		}
		Map<CursedSpiritGrade, double[]> extremes = new EnumMap<>(CursedSpiritGrade.class);
		for (CursedSpiritGrade grade : hp.keySet()) {
			extremes.put(grade, new double[]{
					min(hp.get(grade)), max(hp.get(grade)),
					min(dmg.get(grade)), max(dmg.get(grade)),
					min(spd.get(grade)), max(spd.get(grade))});
		}
		return extremes;
	}

	private static void assertDisjoint(GameTestHelper helper, String fixture,
			Map<CursedSpiritGrade, double[]> extremes, boolean hp, boolean dmg, boolean spd, String what) {
		int o = hp ? 0 : dmg ? 2 : 4;
		helper.assertTrue(extremes.get(CursedSpiritGrade.GRADE_5)[o + 1] < extremes.get(CursedSpiritGrade.GRADE_4)[o],
				diag(fixture, helper, what + " 5 below 4", ""));
		helper.assertTrue(extremes.get(CursedSpiritGrade.GRADE_4)[o + 1] < extremes.get(CursedSpiritGrade.GRADE_3)[o],
				diag(fixture, helper, what + " 4 below 3", ""));
	}


	private CursedSpiritEntity finalizeOne(GameTestHelper helper, int i) {
		return spawnAt(helper, new BlockPos(1 + i % 6, 1, 1 + (i / 6) % 6), i);
	}

	private CursedSpiritEntity spawnAt(GameTestHelper helper, BlockPos rel, int i) {
		EntityType<CursedSpiritEntity> type = switch (i % 3) {
			case 0 -> JujutsuEntities.LESSER_CURSED_SPIRIT;
			case 1 -> JujutsuEntities.CURSED_SPIRIT;
			default -> JujutsuEntities.GREATER_CURSED_SPIRIT;
		};
		ServerLevel level = helper.getLevel();
		CursedSpiritEntity spirit = helper.spawn(type, rel);
		spirit.setPersistenceRequired();
		BlockPos abs = helper.absolutePos(rel);
		spirit.finalizeSpawn(level, level.getCurrentDifficultyAt(abs), EntitySpawnReason.COMMAND, null);
		owned.add(spirit);
		return spirit;
	}

	@SuppressWarnings("unchecked")
	private static EntityType<CursedSpiritEntity> typeOf(CursedSpiritEntity spirit) {
		return (EntityType<CursedSpiritEntity>) spirit.getType();
	}

	private static double min(List<Double> values) {
		double m = Double.POSITIVE_INFINITY;
		for (double v : values) {
			m = Math.min(m, v);
		}
		return m;
	}

	private static double max(List<Double> values) {
		double m = Double.NEGATIVE_INFINITY;
		for (double v : values) {
			m = Math.max(m, v);
		}
		return m;
	}

	private void discardOwned() {
		for (CursedSpiritEntity spirit : owned) {
			spirit.discard();
		}
		owned.clear();
	}

	private static net.minecraft.network.chat.Component diag(
			String fixture, GameTestHelper helper, String what, Object actual) {
		return CursedSpiritTestFixtures.diagnostic(fixture, helper.getTick(), what, "see report", actual);
	}
}
