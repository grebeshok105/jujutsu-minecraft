package jujutsu.mod.client.character.nobara;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Aggregation contract for {@link NobaraEspState#aggregate}.
 *
 * <p>The function filters a snapshot of candidate nail views into per-target groups. Each group
 * carries the nail count, embed depths sorted descending, and the smallest nail entity id as the
 * visual leader (the one that renders the billboard).
 */
final class NobaraEspStateTest {

	private static NobaraEspState.NailView nail(int nailEntityId, int targetEntityId, int depth,
			boolean embedded, boolean ownedByLocal, boolean targetAlive) {
		return new NobaraEspState.NailView(nailEntityId, targetEntityId, depth, embedded, ownedByLocal, targetAlive);
	}

	@Test
	void foreignOwnerIsFilteredOut() {
		Map<Integer, NobaraEspState.TargetEsp> result =
				NobaraEspState.aggregate(List.of(nail(10, 300, 1, true, false, true)));
		assertTrue(result.isEmpty(), "a nail owned by another player must never feed the ESP");
	}

	@Test
	void nonEmbeddedNailIsFilteredOut() {
		Map<Integer, NobaraEspState.TargetEsp> result =
				NobaraEspState.aggregate(List.of(nail(10, 300, 1, false, true, true)));
		assertTrue(result.isEmpty(), "a flying or prepared nail is not an embedded nail");
	}

	@Test
	void deadTargetIsFilteredOut() {
		Map<Integer, NobaraEspState.TargetEsp> result =
				NobaraEspState.aggregate(List.of(nail(10, 300, 2, true, true, false)));
		assertTrue(result.isEmpty(), "a dead target must drop off the ESP immediately");
	}

	@Test
	void negativeTargetIdIsFilteredOut() {
		Map<Integer, NobaraEspState.TargetEsp> result =
				NobaraEspState.aggregate(List.of(nail(10, -1, 1, true, true, true)));
		assertTrue(result.isEmpty(), "a nail without a synced target id has nothing to annotate");
	}

	@Test
	void groupsNailsByTarget() {
		Map<Integer, NobaraEspState.TargetEsp> result = NobaraEspState.aggregate(List.of(
				nail(10, 300, 1, true, true, true),
				nail(11, 300, 2, true, true, true),
				nail(12, 400, 3, true, true, true)));
		assertEquals(2, result.size());
		assertEquals(2, result.get(300).nailCount());
		assertEquals(1, result.get(400).nailCount());
		assertEquals(300, result.get(300).targetId());
		assertEquals(400, result.get(400).targetId());
	}

	@Test
	void leaderIsMinimumEntityId() {
		Map<Integer, NobaraEspState.TargetEsp> result = NobaraEspState.aggregate(List.of(
				nail(83, 300, 1, true, true, true),
				nail(17, 300, 1, true, true, true),
				nail(52, 300, 1, true, true, true)));
		assertEquals(17, result.get(300).leaderNailEntityId(),
				"the billboard owner must be stable across refreshes: the minimum nail entity id");
	}

	@Test
	void depthsSortedDescending() {
		Map<Integer, NobaraEspState.TargetEsp> result = NobaraEspState.aggregate(List.of(
				nail(10, 300, 1, true, true, true),
				nail(11, 300, 3, true, true, true),
				nail(12, 300, 2, true, true, true)));
		assertEquals(List.of(3, 2, 1), result.get(300).nailDepths(),
				"depth pips must render strongest-first");
	}

	@Test
	void emptyInputReturnsEmptyMap() {
		assertTrue(NobaraEspState.aggregate(List.of()).isEmpty());
	}

	@Test
	void mixedFiltersCombineCorrectly() {
		Map<Integer, NobaraEspState.TargetEsp> result = NobaraEspState.aggregate(List.of(
				nail(83, 300, 2, true, true, true),
				nail(84, 300, 1, false, true, true),
				nail(85, 300, 1, true, false, true),
				nail(86, 500, 1, true, true, false)));
		assertEquals(1, result.size(), "filtered nails must not leak into any group");
		NobaraEspState.TargetEsp target = result.get(300);
		assertEquals(1, target.nailCount());
		assertEquals(83, target.leaderNailEntityId());
		assertEquals(List.of(2), target.nailDepths());
	}
}
