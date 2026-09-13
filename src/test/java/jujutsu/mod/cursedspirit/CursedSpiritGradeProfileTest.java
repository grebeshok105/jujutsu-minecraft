package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Grade catalogue invariants (Block 2, Step 1).
 *
 * <p>{@code bandsAreDisjointAcrossAllStats} is the table-level half of the
 * #81 invariant: it pins {@code max(band n) < min(band n-1)} for HP, damage
 * AND speed on the table itself, so a rev-1-class speed overlap can never
 * slip back in unnoticed. The roll-level half (clamp + finals) lives in
 * {@code CursedSpiritRollPolicyTest}.
 */
final class CursedSpiritGradeProfileTest {
	@Test
	void weightsAreNonNegativeAndSumToOneHundred() {
		int total = 0;
		for (CursedSpiritGrade grade : CursedSpiritGrade.values()) {
			int weight = CursedSpiritGradeProfile.v1Weight(grade);
			assertTrue(weight >= 0, grade + " weight must be non-negative");
			total += weight;
		}
		assertEquals(100, total, "v1 weights must sum to 100 for the nextInt bound");
	}

	@Test
	void spawnableGradesCarryContentWhileFutureGradesWeighZero() {
		for (CursedSpiritGrade grade : CursedSpiritGrade.SPAWNABLE_V1) {
			assertTrue(CursedSpiritGradeProfile.v1Weight(grade) > 0, grade + " must be rollable in v1");
		}
		assertEquals(0, CursedSpiritGradeProfile.v1Weight(CursedSpiritGrade.GRADE_2));
		assertEquals(0, CursedSpiritGradeProfile.v1Weight(CursedSpiritGrade.GRADE_1));
	}

	@Test
	void bandsAreDisjointAcrossAllStats() {
		for (CursedSpiritGrade weaker : CursedSpiritGrade.SPAWNABLE_V1) {
			for (CursedSpiritGrade stronger : CursedSpiritGrade.SPAWNABLE_V1) {
				if (stronger.powerRank() != weaker.powerRank() + 1) {
					continue;
				}
				assertTrue(CursedSpiritGradeProfile.health(weaker).max()
						< CursedSpiritGradeProfile.health(stronger).min(),
						"HP band " + weaker + " must end below " + stronger);
				assertTrue(CursedSpiritGradeProfile.damage(weaker).max()
						< CursedSpiritGradeProfile.damage(stronger).min(),
						"damage band " + weaker + " must end below " + stronger);
				assertTrue(CursedSpiritGradeProfile.speed(weaker).max()
						< CursedSpiritGradeProfile.speed(stronger).min(),
						"speed band " + weaker + " must end below " + stronger
								+ " (rev-1 speed overlap class)");
			}
		}
	}

	@Test
	void normSitsInsideEveryBand() {
		for (CursedSpiritGrade grade : CursedSpiritGrade.SPAWNABLE_V1) {
			assertBandSane(CursedSpiritGradeProfile.health(grade), "HP " + grade);
			assertBandSane(CursedSpiritGradeProfile.damage(grade), "damage " + grade);
			assertBandSane(CursedSpiritGradeProfile.speed(grade), "speed " + grade);
		}
	}

	private static void assertBandSane(CursedSpiritGradeBand band, String what) {
		assertTrue(band.min() < band.max(), what + " needs a non-empty band");
		assertTrue(band.norm() >= band.min() && band.norm() <= band.max(),
				what + " norm must sit inside the band");
	}

	@Test
	void futureGradesThrowOnBandAccess() {
		for (CursedSpiritGrade grade : new CursedSpiritGrade[]{
				CursedSpiritGrade.GRADE_2, CursedSpiritGrade.GRADE_1}) {
			assertThrows(IllegalStateException.class, () -> CursedSpiritGradeProfile.health(grade));
			assertThrows(IllegalStateException.class, () -> CursedSpiritGradeProfile.damage(grade));
			assertThrows(IllegalStateException.class, () -> CursedSpiritGradeProfile.speed(grade));
		}
	}

	@Test
	void gradeLevelsAndRanks() {
		assertEquals(5, CursedSpiritGrade.GRADE_5.level());
		assertEquals(1, CursedSpiritGrade.GRADE_5.powerRank());
		assertEquals(3, CursedSpiritGrade.GRADE_3.level());
		assertEquals(3, CursedSpiritGrade.GRADE_3.powerRank());
		assertEquals(1, CursedSpiritGrade.GRADE_1.level());
		assertEquals(5, CursedSpiritGrade.GRADE_1.powerRank());
		assertTrue(CursedSpiritGrade.byLevel(4).isPresent());
		assertTrue(CursedSpiritGrade.byLevel(0).isEmpty());
		assertTrue(CursedSpiritGrade.byLevel(6).isEmpty());
	}

	@Test
	void archetypeRanksArePinnedBalanceValues() {
		assertArchetype(CursedSpiritTier.LESSER, 0.35, 0.40, 0.70);
		assertArchetype(CursedSpiritTier.COMMON, 0.50, 0.50, 0.45);
		assertArchetype(CursedSpiritTier.GREATER, 0.75, 0.65, 0.25);
	}

	private static void assertArchetype(CursedSpiritTier tier, double hp, double damage, double speed) {
		CursedSpiritTierArchetype archetype = CursedSpiritTierArchetype.of(tier);
		assertEquals(hp, archetype.hpRank(), 1e-12, tier + " hpRank");
		assertEquals(damage, archetype.damageRank(), 1e-12, tier + " damageRank");
		assertEquals(speed, archetype.speedRank(), 1e-12, tier + " speedRank");
	}

	@Test
	void registrationDefaultsSitInTheWeakestBand() {
		CursedSpiritGradeStats defaults = CursedSpiritGradeProfile.registrationDefaults();
		assertTrue(defaults.inBandsOf(CursedSpiritGrade.GRADE_5),
				"registration placeholder must be a legal grade-5 body");
	}
}
