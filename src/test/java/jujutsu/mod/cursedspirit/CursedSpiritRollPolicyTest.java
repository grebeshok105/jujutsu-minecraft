package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Roll-curve and order invariants (Block 2, Steps 2–3). All test numbers are
 * fixed by the plan: distribution {@code N = 20000} at ±2%, tail share
 * ≤ 35%, independence {@code |r| < 0.05}, finals invariant at ≥ 500 per
 * (tier × grade).
 *
 * <p>Red-proof ledger: zeroing a weight reshapes the distribution test;
 * widening {@code rollStat} to uniform breaks the tail test; dropping the
 * clamp breaks the finals invariant; swapping two newborn stages breaks the
 * golden vectors (pinned below, computed from the real code at seed
 * provenance {@code 7 / 123456789 / 987654321}).
 */
final class CursedSpiritRollPolicyTest {
	// Golden vectors touch Entity's static init via rollVariant — bootstrap first
	// so this class passes in isolation, not only inside the full suite.
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final int DISTRIBUTION_N = 20000;
	private static final long DISTRIBUTION_SEED = 0xC81D5EEDL;

	@Test
	void selectGradeIndexMatchesOneToOneBoundaries() {
		int[] weights = {60, 30, 10};
		assertEquals(0, CursedSpiritRollPolicy.selectGradeIndex(weights, 0));
		assertEquals(0, CursedSpiritRollPolicy.selectGradeIndex(weights, 59));
		assertEquals(1, CursedSpiritRollPolicy.selectGradeIndex(weights, 60));
		assertEquals(1, CursedSpiritRollPolicy.selectGradeIndex(weights, 89));
		assertEquals(2, CursedSpiritRollPolicy.selectGradeIndex(weights, 90));
		assertEquals(2, CursedSpiritRollPolicy.selectGradeIndex(weights, 99));
	}

	@Test
	void distributionIsSixtyThirtyTenWithinTwoPercent() {
		Map<CursedSpiritGrade, Integer> counts = new EnumMap<>(CursedSpiritGrade.class);
		RandomSource random = RandomSource.create(DISTRIBUTION_SEED);
		for (int i = 0; i < DISTRIBUTION_N; i++) {
			CursedSpiritGrade grade = CursedSpiritRollPolicy.rollGrade(random);
			assertTrue(CursedSpiritGrade.SPAWNABLE_V1.contains(grade), "only 5/4/3 may roll, got " + grade);
			counts.merge(grade, 1, Integer::sum);
		}
		assertShare(counts, CursedSpiritGrade.GRADE_5, 0.60);
		assertShare(counts, CursedSpiritGrade.GRADE_4, 0.30);
		assertShare(counts, CursedSpiritGrade.GRADE_3, 0.10);
	}

	private static void assertShare(Map<CursedSpiritGrade, Integer> counts, CursedSpiritGrade grade,
			double expected) {
		double share = counts.getOrDefault(grade, 0) / (double) DISTRIBUTION_N;
		assertTrue(Math.abs(share - expected) <= 0.02,
				grade + " share " + share + " must sit within ±0.02 of " + expected);
	}

	@Test
	void centreConcentrationBeatsUniformOnTheTails() {
		CursedSpiritGradeBand band = CursedSpiritGradeProfile.health(CursedSpiritGrade.GRADE_4);
		RandomSource random = RandomSource.create(DISTRIBUTION_SEED);
		int rolls = CursedSpiritGradeProfile.statRolls();
		int tail = 0;
		for (int i = 0; i < DISTRIBUTION_N; i++) {
			double value = CursedSpiritRollPolicy.rollStat(random, band, rolls);
			double quartile = band.width() / 4.0;
			if (value < band.min() + quartile || value > band.max() - quartile) {
				tail++;
			}
		}
		double tailShare = tail / (double) DISTRIBUTION_N;
		assertTrue(tailShare <= 0.35,
				"outer quartiles must stay rare (<= 35% vs uniform's 50%), got " + tailShare);
	}

	@Test
	void hpAndSpeedRollsAreIndependent() {
		RandomSource random = RandomSource.create(DISTRIBUTION_SEED);
		CursedSpiritTierArchetype archetype = CursedSpiritTierArchetype.of(CursedSpiritTier.COMMON);
		double sumHp = 0.0;
		double sumSpd = 0.0;
		double sumHpSpd = 0.0;
		double sumHp2 = 0.0;
		double sumSpd2 = 0.0;
		for (int i = 0; i < DISTRIBUTION_N; i++) {
			CursedSpiritGradeStats stats =
					CursedSpiritRollPolicy.rollStats(random, CursedSpiritGrade.GRADE_4, archetype);
			sumHp += stats.maxHealth();
			sumSpd += stats.movementSpeed();
			sumHpSpd += stats.maxHealth() * stats.movementSpeed();
			sumHp2 += stats.maxHealth() * stats.maxHealth();
			sumSpd2 += stats.movementSpeed() * stats.movementSpeed();
		}
		double n = DISTRIBUTION_N;
		double covariance = (sumHpSpd - sumHp * sumSpd / n) / n;
		double varHp = (sumHp2 - sumHp * sumHp / n) / n;
		double varSpd = (sumSpd2 - sumSpd * sumSpd / n) / n;
		double correlation = covariance / Math.sqrt(varHp * varSpd);
		assertTrue(Math.abs(correlation) < 0.05, "HP x speed |r| must stay < 0.05, got " + correlation);
	}

	@Test
	void finalsStayDisjointAcrossGradesForEveryTier() {
		for (CursedSpiritTier tier : CursedSpiritTier.values()) {
			CursedSpiritTierArchetype archetype = CursedSpiritTierArchetype.of(tier);
		 double maxHp5 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_5, true, false, false);
			double minHp4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, false, false, false);
			double maxHp4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, true, false, false);
			double minHp3 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_3, false, false, false);
			assertTrue(maxHp5 < minHp4, tier + " HP finals: max(5)=" + maxHp5 + " must sit below min(4)=" + minHp4);
			assertTrue(maxHp4 < minHp3, tier + " HP finals: max(4)=" + maxHp4 + " must sit below min(3)=" + minHp3);

			double maxDmg5 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_5, true, true, false);
			double minDmg4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, false, true, false);
			double maxDmg4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, true, true, false);
			double minDmg3 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_3, false, true, false);
			assertTrue(maxDmg5 < minDmg4, tier + " damage finals must stay disjoint 5/4");
			assertTrue(maxDmg4 < minDmg3, tier + " damage finals must stay disjoint 4/3");

			double maxSpd5 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_5, true, false, true);
			double minSpd4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, false, false, true);
			double maxSpd4 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_4, true, false, true);
			double minSpd3 = maxFinal(tier, archetype, CursedSpiritGrade.GRADE_3, false, false, true);
			assertTrue(maxSpd5 < minSpd4, tier + " speed finals must stay disjoint 5/4");
			assertTrue(maxSpd4 < minSpd3, tier + " speed finals must stay disjoint 4/3");
		}
	}

	private static double maxFinal(CursedSpiritTier tier, CursedSpiritTierArchetype archetype,
			CursedSpiritGrade grade, boolean takeMax, boolean damage, boolean speed) {
		RandomSource random = RandomSource.create(tierSeed(tier, grade));
		double extreme = takeMax ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
		for (int i = 0; i < 500; i++) {
			CursedSpiritGradeStats stats = CursedSpiritRollPolicy.rollStats(random, grade, archetype);
			assertTrue(stats.inBandsOf(grade), "every final must sit in its grade band: " + stats);
			double value = damage ? stats.attackDamage()
					: speed ? stats.movementSpeed() : stats.maxHealth();
			extreme = takeMax ? Math.max(extreme, value) : Math.min(extreme, value);
		}
		return extreme;
	}

	private static long tierSeed(CursedSpiritTier tier, CursedSpiritGrade grade) {
		return 1000L + tier.ordinal() * 100L + grade.level();
	}

	@Test
	void tierRankMovesTheValueButNeverLeavesTheBand() {
		CursedSpiritGradeBand band = CursedSpiritGradeProfile.damage(CursedSpiritGrade.GRADE_4);
		RandomSource low = RandomSource.create(DISTRIBUTION_SEED);
		RandomSource high = RandomSource.create(DISTRIBUTION_SEED);
		double sumLow = 0.0;
		double sumHigh = 0.0;
		int n = 2000;
		for (int i = 0; i < n; i++) {
			double atBottom = CursedSpiritRollPolicy
					.rollStats(low, CursedSpiritGrade.GRADE_4, new CursedSpiritTierArchetype(0.5, 0.0, 0.5))
					.attackDamage();
			double atTop = CursedSpiritRollPolicy
					.rollStats(high, CursedSpiritGrade.GRADE_4, new CursedSpiritTierArchetype(0.5, 1.0, 0.5))
					.attackDamage();
			assertTrue(band.contains(atBottom), "rank=0 final must stay in band: " + atBottom);
			assertTrue(band.contains(atTop), "rank=1 final must stay in band: " + atTop);
			sumLow += atBottom;
			sumHigh += atTop;
		}
		assertTrue(sumHigh / n > sumLow / n + band.width() * 0.1,
				"rank=1 must average clearly above rank=0 on the same stream");
	}

	@Test
	void mutatedBandMovesTheDerivation() {
		CursedSpiritGradeBand mutated = new CursedSpiritGradeBand(100.0, 110.0, 130.0);
		RandomSource random = RandomSource.create(DISTRIBUTION_SEED);
		for (int i = 0; i < 200; i++) {
			double value = CursedSpiritRollPolicy.rollStat(
					random, mutated, CursedSpiritGradeProfile.statRolls());
			assertTrue(mutated.contains(value), "roll must follow the passed band, got " + value);
			assertTrue(value >= 100.0, "mutated band floor must bind, got " + value);
		}
	}

	@Test
	void rollStatRejectsEmptyDraws() {
		CursedSpiritGradeBand band = CursedSpiritGradeProfile.health(CursedSpiritGrade.GRADE_5);
		assertThrows(IllegalArgumentException.class,
				() -> CursedSpiritRollPolicy.rollStat(RandomSource.create(1L), band, 0));
	}

	@Test
	void resolveLoadedCoversTheModel() {
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(5).isPresent());
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(3).isPresent());
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(2).isPresent());
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(0).isEmpty());
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(6).isEmpty());
		assertTrue(CursedSpiritRollPolicy.resolveLoaded(-1).isEmpty());
	}

	/**
	 * Golden newborn vectors: fixed seeds through the real
	 * {@code grade → variant → stats} order. Computed headless from the
	 * production code (see {@code block-2-report.md} for the command);
	 * swapping any two stages changes the stream and reds this test — the
	 * order red-proof (determinism alone would not catch a swap).
	 */
	@Test
	void goldenNewbornVectorsPinGradeVariantAndStats() {
		assertNewborn(7L, CursedSpiritTier.LESSER, 5, "gulber",
				16.719350530418650, 3.687218657875742, 0.241618808909144);
		assertNewborn(7L, CursedSpiritTier.COMMON, 5, "guzzler",
				17.319350530418653, 3.787218657875742, 0.237868808909144);
		assertNewborn(7L, CursedSpiritTier.GREATER, 5, "wistiver",
				18.319350530418653, 3.937218657875742, 0.234868808909144);
		assertNewborn(123456789L, CursedSpiritTier.LESSER, 4, "prowler",
				32.073730652319330, 7.434497003813500, 0.271507292304836);
		assertNewborn(123456789L, CursedSpiritTier.COMMON, 4, "guzzler",
				33.123730652319324, 7.584497003813500, 0.267757292304836);
		assertNewborn(123456789L, CursedSpiritTier.GREATER, 4, "wistiver",
				34.873730652319324, 7.809497003813500, 0.264757292304836);
		assertNewborn(987654321L, CursedSpiritTier.LESSER, 5, "floating_curse",
				15.587343860561030, 3.367981380992531, 0.237318309094789);
		assertNewborn(987654321L, CursedSpiritTier.COMMON, 5, "blud",
				16.187343860561030, 3.467981380992531, 0.233568309094789);
		assertNewborn(987654321L, CursedSpiritTier.GREATER, 5, "wistiver",
				17.187343860561030, 3.617981380992531, 0.230568309094789);
	}

	private static void assertNewborn(long seed, CursedSpiritTier tier, int gradeLevel, String variantId,
			double hp, double damage, double speed) {
		CursedSpiritRollPolicy.Newborn newborn =
				CursedSpiritRollPolicy.rollNewborn(RandomSource.create(seed), tier);
		assertEquals(gradeLevel, newborn.grade().level(), "seed " + seed + " " + tier + " grade");
		assertEquals(variantId, newborn.variant().id(), "seed " + seed + " " + tier + " variant");
		assertEquals(hp, newborn.stats().maxHealth(), 1e-12, "seed " + seed + " " + tier + " hp");
		assertEquals(damage, newborn.stats().attackDamage(), 1e-12, "seed " + seed + " " + tier + " damage");
		assertEquals(speed, newborn.stats().movementSpeed(), 1e-12, "seed " + seed + " " + tier + " speed");
	}

	@Test
	void swappedVariantAndStatsStagesMissTheGoldenVectors() {
		long[] seeds = {7L, 123456789L, 987654321L};
		for (long seed : seeds) {
			RandomSource production = RandomSource.create(seed);
			CursedSpiritRollPolicy.Newborn golden =
					CursedSpiritRollPolicy.rollNewborn(production, CursedSpiritTier.COMMON);
			RandomSource swapped = RandomSource.create(seed);
			CursedSpiritGrade grade = CursedSpiritRollPolicy.rollGrade(swapped);
			CursedSpiritGradeStats statsFirst = CursedSpiritRollPolicy.rollStats(
					swapped, grade, CursedSpiritTierArchetype.of(CursedSpiritTier.COMMON));
			CursedSpiritVariant variantSecond =
					CursedSpiritEntity.rollVariant(CursedSpiritTier.COMMON, swapped);
			boolean identical = variantSecond == golden.variant()
					&& statsFirst.maxHealth() == golden.stats().maxHealth()
					&& statsFirst.attackDamage() == golden.stats().attackDamage()
					&& statsFirst.movementSpeed() == golden.stats().movementSpeed();
			assertNotEquals(true, identical,
					"seed " + seed + ": stats-before-variant must diverge from the golden order");
		}
	}
}
