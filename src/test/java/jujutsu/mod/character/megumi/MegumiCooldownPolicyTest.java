package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MegumiCooldownPolicyTest {
	@Test
	void summonHasNoCooldownAndRecallAndLossUseApprovedDurations() {
		assertEquals(0, MegumiCooldownPolicy.duration(MegumiCooldownPolicy.Cause.NONE));
		assertEquals(240, MegumiCooldownPolicy.duration(MegumiCooldownPolicy.Cause.RECALL));
		assertEquals(600, MegumiCooldownPolicy.duration(MegumiCooldownPolicy.Cause.FINAL_LOSS));
	}

	@Test
	void aShorterRequestCannotReplaceALongerActiveCooldown() {
		assertEquals(600, MegumiCooldownPolicy.preservedRemaining(600, 240));
		assertEquals(600, MegumiCooldownPolicy.preservedRemaining(240, 600));
		assertEquals(30, MegumiCooldownPolicy.preservedRemaining(0, 30));
	}
}
