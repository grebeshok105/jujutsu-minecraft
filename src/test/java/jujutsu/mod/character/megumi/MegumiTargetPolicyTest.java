package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MegumiTargetPolicyTest {
	@Test
	void ownerOwnDogAllySpectatorAndDeadTargetsAreRefused() {
		assertFalse(MegumiTargetPolicy.accepts(facts(true, true, true, true, false, false, false)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, true, true, true, false, true, false)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, true, true, true, false, false, true)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, true, true, true, true, false, false)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, false, true, true, false, false, false)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, true, false, true, false, false, false)));
		assertFalse(MegumiTargetPolicy.accepts(facts(false, true, true, false, false, false, false)));
	}

	@Test
	void livingLoadedHostileInTheSameLevelIsAccepted() {
		assertTrue(MegumiTargetPolicy.accepts(facts(false, true, true, true, false, false, false)));
	}

	private static MegumiTargetPolicy.Facts facts(
			boolean owner, boolean alive, boolean loaded, boolean sameLevel,
			boolean spectator, boolean ownDog, boolean allied) {
		return new MegumiTargetPolicy.Facts(owner, alive, loaded, sameLevel, spectator, ownDog, allied);
	}
}
