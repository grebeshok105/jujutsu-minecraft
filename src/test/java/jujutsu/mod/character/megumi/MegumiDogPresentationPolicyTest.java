package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MegumiDogPresentationPolicyTest {
	@Test
	void phasesOwnTheirExactTransitionAndCombatGates() {
		assertEquals(MegumiDogPresentationPolicy.Phase.MATERIALIZING,
				MegumiDogPresentationPolicy.phaseAfterTick(
						MegumiDogPresentationPolicy.Phase.MATERIALIZING, 15));
		assertEquals(MegumiDogPresentationPolicy.Phase.ACTIVE,
				MegumiDogPresentationPolicy.phaseAfterTick(
						MegumiDogPresentationPolicy.Phase.MATERIALIZING, 16));
		assertEquals(MegumiDogPresentationPolicy.Phase.ACTIVE,
				MegumiDogPresentationPolicy.phaseAfterTick(
						MegumiDogPresentationPolicy.Phase.ACTIVE, 200));

		assertFalse(MegumiDogPresentationPolicy.recallComplete(11));
		assertTrue(MegumiDogPresentationPolicy.recallComplete(12));
		assertFalse(MegumiDogPresentationPolicy.combatEnabled(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING));
		assertTrue(MegumiDogPresentationPolicy.combatEnabled(
				MegumiDogPresentationPolicy.Phase.ACTIVE));
		assertFalse(MegumiDogPresentationPolicy.combatEnabled(
				MegumiDogPresentationPolicy.Phase.RECALLING));
	}

	@Test
	void riseAndSinkUseNormalizedPhaseProgressWithoutMovingTheEntity() {
		assertEquals(0.0f, MegumiDogPresentationPolicy.progress(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING, 0, 0.0f));
		assertEquals(0.5f, MegumiDogPresentationPolicy.progress(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING, 8, 0.0f));
		assertEquals(1.0f, MegumiDogPresentationPolicy.progress(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING, 16, 0.0f));
		assertEquals(-1.0f, MegumiDogPresentationPolicy.verticalOffset(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING, 0.0f));
		assertEquals(-0.5f, MegumiDogPresentationPolicy.verticalOffset(
				MegumiDogPresentationPolicy.Phase.MATERIALIZING, 0.5f));
		assertEquals(0.0f, MegumiDogPresentationPolicy.verticalOffset(
				MegumiDogPresentationPolicy.Phase.ACTIVE, 1.0f));

		assertEquals(0.5f, MegumiDogPresentationPolicy.progress(
				MegumiDogPresentationPolicy.Phase.RECALLING, 6, 0.0f));
		assertEquals(-0.5f, MegumiDogPresentationPolicy.verticalOffset(
				MegumiDogPresentationPolicy.Phase.RECALLING, 0.5f));
		assertEquals(-1.0f, MegumiDogPresentationPolicy.verticalOffset(
				MegumiDogPresentationPolicy.Phase.RECALLING, 1.0f));
	}
}
