package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The short failure memory (issue #107 §15): a fresh failure de-weights the action below the
 * pounce-skip threshold, the weight recovers across the window, and nothing is remembered past it.
 */
class MegumiFailureMemoryTest {

	@Test
	void aFreshFailureDeweightsBelowTheSkipThreshold() {
		UUID body = UUID.randomUUID();
		MegumiFailureMemory.recordFailure(body, "pounce", 1_000L);
		double weight = MegumiFailureMemory.weight(body, "pounce", 1_000L);
		assertTrue(weight < MegumiProfile.POUNCE_RETRY_MIN_WEIGHT,
				"one fresh failure must stop the immediate retry (R16): " + weight);
		assertTrue(weight >= MegumiShikigamiProfile.FAILURE_FLOOR,
				"the floor holds: " + weight);
		MegumiFailureMemory.clear(body);
	}

	@Test
	void theWeightRecoversAcrossTheWindow() {
		UUID body = UUID.randomUUID();
		MegumiFailureMemory.recordFailure(body, "pounce", 1_000L);
		double early = MegumiFailureMemory.weight(body, "pounce", 1_010L);
		double late = MegumiFailureMemory.weight(body, "pounce",
				1_000L + MegumiShikigamiProfile.FAILURE_WINDOW_TICKS / 2);
		double expired = MegumiFailureMemory.weight(body, "pounce",
				1_000L + MegumiShikigamiProfile.FAILURE_WINDOW_TICKS + 1);
		assertTrue(late > early, "the weight recovers: " + early + " -> " + late);
		assertEquals(1.0, expired, "past the window the failure is forgotten (R15)");
		MegumiFailureMemory.clear(body);
	}

	@Test
	void repeatedFailuresSitAtTheFloor() {
		UUID body = UUID.randomUUID();
		for (int i = 0; i < 5; i++) {
			MegumiFailureMemory.recordFailure(body, "pounce", 1_000L + i);
		}
		// Recovery runs off the newest stamp, so the floor is exact only on its own tick.
		assertEquals(MegumiShikigamiProfile.FAILURE_FLOOR,
				MegumiFailureMemory.weight(body, "pounce", 1_004L),
				"five fresh failures bottom out at the floor");
		assertTrue(MegumiFailureMemory.weight(body, "pounce", 1_004L + MegumiShikigamiProfile.FAILURE_WINDOW_TICKS / 10)
						< MegumiProfile.POUNCE_RETRY_MIN_WEIGHT,
				"and stay below the skip threshold well past the last failure");
		MegumiFailureMemory.clear(body);
	}

	@Test
	void failuresAreScopedPerBodyAndPerAction() {
		UUID body = UUID.randomUUID();
		UUID other = UUID.randomUUID();
		MegumiFailureMemory.recordFailure(body, "pounce", 1_000L);
		assertEquals(1.0, MegumiFailureMemory.weight(other, "pounce", 1_000L),
				"another body is unaffected");
		assertEquals(1.0, MegumiFailureMemory.weight(body, "dive", 1_000L),
				"another action is unaffected");
		MegumiFailureMemory.clear(body);
	}

	@Test
	void clearDropsTheMemory() {
		UUID body = UUID.randomUUID();
		MegumiFailureMemory.recordFailure(body, "pounce", 1_000L);
		MegumiFailureMemory.clear(body);
		assertEquals(1.0, MegumiFailureMemory.weight(body, "pounce", 1_000L));
	}
}
