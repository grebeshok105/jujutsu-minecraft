package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MegumiElephantBrainTest {
	@Test
	void closeSicDistancesMayStartTheJet() {
		assertTrue(MegumiElephantBrain.jetTriggerInReach(0.0));
		assertTrue(MegumiElephantBrain.jetTriggerInReach(5.0));
		assertTrue(MegumiElephantBrain.jetTriggerInReach(
				MegumiShikigamiProfile.ELEPHANT_JET_LENGTH
						+ MegumiShikigamiProfile.ELEPHANT_TRUNK_FORWARD),
				"the corridor's far edge is still hosed");
	}

	@Test
	void theOldSixteenBlockTriggerRefusesToFireABlankVolley() {
		assertFalse(MegumiElephantBrain.jetTriggerInReach(16.0),
				"16 blocks outruns the 12-block corridor from a trunk 1.2 ahead: a jet from there "
						+ "spends the windup, twenty pulses, and a 220-tick lockout on empty air");
		assertFalse(MegumiElephantBrain.jetTriggerInReach(
				MegumiShikigamiProfile.SIC_RANGE),
				"the sic acquisition range must stay wider than the jet's real reach");
	}
}
