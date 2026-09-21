package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.cursedincident.policy.SealPolicy;
import jujutsu.mod.cursedincident.policy.TemplateRollPolicy;
import org.junit.jupiter.api.Test;

/** R5/R6 — grade profile table is explicit and monotonic. */
class TemplateRollPolicyTest {
	@Test
	void escalationSpeedTableIsPinned() {
		assertEquals(0.70, TemplateRollPolicy.escalationSpeedMul(1), 1.0e-12);
		assertEquals(0.85, TemplateRollPolicy.escalationSpeedMul(2), 1.0e-12);
		assertEquals(1.00, TemplateRollPolicy.escalationSpeedMul(3), 1.0e-12);
		assertEquals(1.20, TemplateRollPolicy.escalationSpeedMul(4), 1.0e-12);
		assertEquals(1.40, TemplateRollPolicy.escalationSpeedMul(5), 1.0e-12);
	}

	@Test
	void strongerGradesHaveLargerRadiusAndMoreResilience() {
		assertTrue(TemplateRollPolicy.maxRadiusMul(1) > TemplateRollPolicy.maxRadiusMul(5));
		assertTrue(TemplateRollPolicy.resilience(1) > TemplateRollPolicy.resilience(5));
	}

	@Test
	void strongerGradesHaveMoreCurseBiasAndAnomalies() {
		assertTrue(TemplateRollPolicy.curseTierBias(1) > TemplateRollPolicy.curseTierBias(5));
		assertTrue(TemplateRollPolicy.anomalyChance(1) > TemplateRollPolicy.anomalyChance(5));
	}

	@Test
	void sealDifficultyIsThreeTiered() {
		assertEquals(3, TemplateRollPolicy.sealDifficulty(1));
		assertEquals(3, TemplateRollPolicy.sealDifficulty(2));
		assertEquals(2, TemplateRollPolicy.sealDifficulty(3));
		assertEquals(2, TemplateRollPolicy.sealDifficulty(4));
		assertEquals(1, TemplateRollPolicy.sealDifficulty(5));
	}

	@Test
	void sealPolicySharesGradeRequirement() {
		for (int grade = 1; grade <= 5; grade++) {
			assertEquals(TemplateRollPolicy.sealDifficulty(grade), SealPolicy.requiredTier(grade));
		}
	}

	@Test
	void gradeInputsClampToSupportedRange() {
		assertEquals(1, TemplateRollPolicy.clampGrade(-5));
		assertEquals(5, TemplateRollPolicy.clampGrade(99));
		assertEquals(0.70, TemplateRollPolicy.escalationSpeedMul(-1), 1.0e-12);
		assertEquals(1.40, TemplateRollPolicy.escalationSpeedMul(99), 1.0e-12);
	}

	@Test
	void dependentCenterConstantIsExplicit() {
		assertEquals(1, TemplateRollPolicy.MAX_DEPENDENT_CENTERS);
	}

	@Test
	void eachAxisIsFiniteAndPositiveWhereRequired() {
		for (int grade = 1; grade <= 5; grade++) {
			assertTrue(Double.isFinite(TemplateRollPolicy.maxRadiusMul(grade)));
			assertTrue(TemplateRollPolicy.maxRadiusMul(grade) > 0.0);
			assertTrue(TemplateRollPolicy.escalationSpeedMul(grade) > 0.0);
			assertTrue(TemplateRollPolicy.resilience(grade) > 0.0);
		}
	}
}
