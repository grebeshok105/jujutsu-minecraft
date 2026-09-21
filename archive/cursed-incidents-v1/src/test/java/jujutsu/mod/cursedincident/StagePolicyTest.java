package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import jujutsu.mod.cursedincident.policy.StagePolicy;
import org.junit.jupiter.api.Test;

/** R13/R14/R15 — deterministic five-stage ladder and age thresholds. */
class StagePolicyTest {
	@Test
	void ladderHasExactlyFiveOrderedStages() {
		assertEquals(List.of(IncidentStage.INITIAL, IncidentStage.GROWING, IncidentStage.INFESTED,
				IncidentStage.CRITICAL, IncidentStage.CATASTROPHIC), List.of(IncidentStage.values()));
	}

	@Test
	void nextStopsAtCatastrophic() {
		assertSame(IncidentStage.GROWING, IncidentStage.INITIAL.next());
		assertSame(IncidentStage.CATASTROPHIC, IncidentStage.CRITICAL.next());
		assertSame(IncidentStage.CATASTROPHIC, IncidentStage.CATASTROPHIC.next());
	}

	@Test
	void exactGrowingBoundaryIsInclusive() {
		assertEquals(IncidentStage.INITIAL, StagePolicy.stageForAge(47_999, 1.0));
		assertEquals(IncidentStage.GROWING, StagePolicy.stageForAge(48_000, 1.0));
	}

	@Test
	void canonicalCycleEndsAtTwelveDays() {
		assertEquals(IncidentStage.CATASTROPHIC, StagePolicy.stageForAge(288_000, 1.0));
		assertEquals(IncidentStage.CATASTROPHIC, StagePolicy.stageForAge(400_000, 1.0));
	}

	@Test
	void fasterGradeHasLowerThresholdThanSlowerGrade() {
		assertTrue(StagePolicy.thresholdFor(IncidentStage.GROWING, 0.7)
				< StagePolicy.thresholdFor(IncidentStage.GROWING, 1.4));
		assertEquals(33_600, StagePolicy.thresholdFor(IncidentStage.GROWING, 0.7));
		assertEquals(67_200, StagePolicy.thresholdFor(IncidentStage.GROWING, 1.4));
	}

	@Test
	void transitionJumpNeverSkipsStages() {
		assertEquals(List.of(IncidentStage.GROWING, IncidentStage.INFESTED,
				IncidentStage.CRITICAL, IncidentStage.CATASTROPHIC),
				StagePolicy.transitionsBetween(IncidentStage.INITIAL, 0, 300_000, 1.0));
	}

	@Test
	void transitionPolicyIsForwardOnly() {
		assertTrue(StagePolicy.transitionsBetween(IncidentStage.CRITICAL, 216_000, 100_000, 1.0).isEmpty());
		assertTrue(StagePolicy.transitionsBetween(IncidentStage.CATASTROPHIC, 0, 300_000, 1.0).isEmpty());
	}

	@Test
	void unknownNamesUseExplicitParserResult() {
		assertTrue(IncidentStage.isKnown("critical"));
		assertEquals(IncidentStage.CRITICAL, IncidentStage.byName("CrItIcAl"));
		assertFalse(IncidentStage.isKnown("scar"));
		assertEquals(IncidentStage.INITIAL, IncidentStage.byNameOrDefault("scar"));
	}
}
