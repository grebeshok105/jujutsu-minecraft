package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Who Max Elephant's presence may hurt (issue #79). The policy exists to keep two questions apart
 * that look like one: {@link MegumiShikigamiFriendlyFire#isProtected} answers "may this take
 * damage at all" and protects allies; the presence must shove allies while sparing them the damage.
 * The three hostility signals are the damage half, and the window that keeps a fresh aggressor
 * worth answering for is the only stateful part — both cheap to pin here.
 */
class MegumiHostilityPolicyTest {

	@Test
	void anySingleSignalIsEnough() {
		assertFalse(MegumiHostilityPolicy.isHostile(false, false, false),
				"a neutral, unaimed, non-aggressor body is not hostile");
		assertTrue(MegumiHostilityPolicy.isHostile(true, false, false),
				"a hostile archetype is enough on its own");
		assertTrue(MegumiHostilityPolicy.isHostile(false, true, false),
				"a body aiming at the owner is enough on its own");
		assertTrue(MegumiHostilityPolicy.isHostile(false, false, true),
				"a fresh aggressor is enough on its own, even without the archetype");
	}

	@Test
	void theAggressorWindowCloses() {
		int window = MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS;
		long hit = 1_000L;
		assertTrue(MegumiHostilityPolicy.aggressorFresh(hit, hit), "the hit tick itself is fresh");
		assertTrue(MegumiHostilityPolicy.aggressorFresh(hit + window, hit),
				"the last tick of the window is still fresh");
		assertFalse(MegumiHostilityPolicy.aggressorFresh(hit + window + 1, hit),
				"one tick past the window the aggressor is forgotten");
	}

	@Test
	void neverHurtMeansNeverFresh() {
		int window = MegumiShikigamiProfile.ELEPHANT_PRESENCE_AGGRESSION_WINDOW_TICKS;
		// A body that never hurt the owner reports timestamp 0; a running world is far past the
		// window, so the freshness signal must stay silent — the "who" test fences it anyway.
		assertFalse(MegumiHostilityPolicy.aggressorFresh(window + 1L, 0L),
				"a never-hurt timestamp never reads as a fresh aggressor");
	}
}
