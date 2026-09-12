package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MegumiShikigamiPresentationPolicyTest {
	private static final int MATERIALIZE = 16;
	private static final int RECALL = 12;

	@Test
	void materializingHandsOverToActiveExactlyAtItsWindow() {
		assertEquals(MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING,
				MegumiShikigamiPresentationPolicy.phaseAfterTick(
						MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, MATERIALIZE - 1, MATERIALIZE));
		assertEquals(MegumiShikigamiPresentationPolicy.Phase.ACTIVE,
				MegumiShikigamiPresentationPolicy.phaseAfterTick(
						MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, MATERIALIZE, MATERIALIZE));
	}

	@Test
	void anActiveOrRecallingBodyNeverReturnedToMaterializing() {
		assertEquals(MegumiShikigamiPresentationPolicy.Phase.ACTIVE,
				MegumiShikigamiPresentationPolicy.phaseAfterTick(
						MegumiShikigamiPresentationPolicy.Phase.ACTIVE, MATERIALIZE + 40, MATERIALIZE));
		assertEquals(MegumiShikigamiPresentationPolicy.Phase.RECALLING,
				MegumiShikigamiPresentationPolicy.phaseAfterTick(
						MegumiShikigamiPresentationPolicy.Phase.RECALLING, MATERIALIZE + 40, MATERIALIZE));
	}

	@Test
	void onlyAnActiveBodyFights() {
		assertFalse(MegumiShikigamiPresentationPolicy.combatEnabled(
				MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING));
		assertTrue(MegumiShikigamiPresentationPolicy.combatEnabled(
				MegumiShikigamiPresentationPolicy.Phase.ACTIVE));
		assertFalse(MegumiShikigamiPresentationPolicy.combatEnabled(
				MegumiShikigamiPresentationPolicy.Phase.RECALLING));
	}

	@Test
	void recallCompletesAtItsOwnWindow() {
		assertFalse(MegumiShikigamiPresentationPolicy.recallComplete(RECALL - 1, RECALL));
		assertTrue(MegumiShikigamiPresentationPolicy.recallComplete(RECALL, RECALL));
	}

	@Test
	void progressIsClampedAndActiveIsAlwaysComplete() {
		assertEquals(1.0f, MegumiShikigamiPresentationPolicy.progress(
				MegumiShikigamiPresentationPolicy.Phase.ACTIVE, 2, 0.0f, MATERIALIZE, RECALL));
		assertEquals(0.5f, MegumiShikigamiPresentationPolicy.progress(
				MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, 8, 0.0f, MATERIALIZE, RECALL));
		assertEquals(1.0f, MegumiShikigamiPresentationPolicy.progress(
				MegumiShikigamiPresentationPolicy.Phase.RECALLING, RECALL + 5, 0.5f, MATERIALIZE, RECALL));
	}

	@Test
	void theBodyRisesOutOfTheFloorAndSinksBackIntoIt() {
		assertEquals(-1.0f, MegumiShikigamiPresentationPolicy.verticalOffset(
				MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, 0.0f));
		assertEquals(0.0f, MegumiShikigamiPresentationPolicy.verticalOffset(
				MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING, 1.0f));
		assertEquals(0.0f, MegumiShikigamiPresentationPolicy.verticalOffset(
				MegumiShikigamiPresentationPolicy.Phase.ACTIVE, 0.4f));
		assertEquals(-1.0f, MegumiShikigamiPresentationPolicy.verticalOffset(
				MegumiShikigamiPresentationPolicy.Phase.RECALLING, 1.0f));
	}

	@Test
	void everyPhaseSurvivesTheNetworkRoundTrip() {
		for (MegumiShikigamiPresentationPolicy.Phase phase : MegumiShikigamiPresentationPolicy.Phase.values()) {
			assertEquals(phase, MegumiShikigamiPresentationPolicy.Phase.fromNetworkId(phase.networkId()));
		}
	}
}
