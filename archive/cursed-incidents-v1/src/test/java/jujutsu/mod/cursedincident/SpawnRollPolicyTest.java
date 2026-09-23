package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import jujutsu.mod.cursedincident.policy.SpawnRollPolicy;
import jujutsu.mod.cursedincident.policy.TemplateRollPolicy;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** R1/R5/R20/R21/R22/R23/R25 — seeded, weighted and bounded rolls. */
class SpawnRollPolicyTest {
	@Test
	void sourceRollIsWithinPinnedObjectWeight() {
		int objects = 0;
		for (int seed = 0; seed < 10_000; seed++) {
			if (SpawnRollPolicy.rollSourceKind(RandomSource.create(seed)) == SourceKind.OBJECT) {
				objects++;
			}
		}
		double ratio = objects / 10_000.0;
		assertTrue(ratio >= 0.77 && ratio <= 0.83, "object ratio=" + ratio);
	}

	@Test
	void sameSeedRollsIdenticalParams() {
		IncidentTemplate template = IncidentTemplates.CATACLYSM;
		IncidentParams first = SpawnRollPolicy.rollParams(RandomSource.create(1234L), template, 2);
		IncidentParams second = SpawnRollPolicy.rollParams(RandomSource.create(1234L), template, 2);
		assertEquals(first, second);
	}

	@Test
	void differentSeedsUsuallyDiverge() {
		IncidentParams first = SpawnRollPolicy.rollParams(RandomSource.create(1L), IncidentTemplates.BLIGHT, 3);
		IncidentParams second = SpawnRollPolicy.rollParams(RandomSource.create(2L), IncidentTemplates.BLIGHT, 3);
		assertNotEquals(first, second);
	}

	@Test
	void templateWeightsAreRespectedAtNeutralGrade() {
		Map<String, Integer> counts = new HashMap<>();
		for (int seed = 0; seed < 10_000; seed++) {
			IncidentTemplate template = SpawnRollPolicy.rollTemplate(RandomSource.create(seed), IncidentTemplates.ALL, 1.0);
			counts.merge(template.id(), 1, Integer::sum);
		}
		assertTrue(counts.getOrDefault("blight", 0) > 2_500);
		assertTrue(counts.getOrDefault("nest", 0) > 2_000);
		assertTrue(counts.getOrDefault("corruption", 0) > 1_500);
		assertTrue(counts.getOrDefault("haunting", 0) > 1_000);
		assertTrue(counts.getOrDefault("cataclysm", 0) > 600);
	}

	@Test
	void gradeAndStageAreIndependentAxes() {
		IncidentParams params = SpawnRollPolicy.rollParams(RandomSource.create(8L), IncidentTemplates.NEST, 1);
		assertNotNull(params);
		assertEquals(0.70 * IncidentTemplates.NEST.escalationMul(), params.escalationSpeedMul(), 1.0e-9);
		assertEquals(IncidentStage.CATASTROPHIC, jujutsu.mod.cursedincident.policy.StagePolicy.stageForAge(
				288_000, params.escalationSpeedMul()));
	}

	@Test
	void gradeDerivesEveryRequiredAxis() {
		assertNotEquals(TemplateRollPolicy.maxRadiusMul(1), TemplateRollPolicy.maxRadiusMul(5));
		assertNotEquals(TemplateRollPolicy.escalationSpeedMul(1), TemplateRollPolicy.escalationSpeedMul(5));
		assertNotEquals(TemplateRollPolicy.curseTierBias(1), TemplateRollPolicy.curseTierBias(5));
		assertNotEquals(TemplateRollPolicy.sealDifficulty(1), TemplateRollPolicy.sealDifficulty(5));
		assertNotEquals(TemplateRollPolicy.anomalyChance(1), TemplateRollPolicy.anomalyChance(5));
		assertNotEquals(TemplateRollPolicy.resilience(1), TemplateRollPolicy.resilience(5));
	}

	@Test
	void zoneRollHasOnlyPinnedGeometryFamilies() {
		for (int seed = 0; seed < 200; seed++) {
			String shape = SpawnRollPolicy.rollZoneShape(RandomSource.create(seed));
			assertTrue(shape.equals("sphere") || shape.equals("column"));
		}
	}

	@Test
	void dependentCenterBoundIsOne() {
		assertEquals(1, TemplateRollPolicy.MAX_DEPENDENT_CENTERS);
		assertTrue(SpawnRollPolicy.DEFAULT_DWELL_TICKS > 0);
	}
}
