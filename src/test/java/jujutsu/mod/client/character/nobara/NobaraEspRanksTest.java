package jujutsu.mod.client.character.nobara;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Classification contract for {@link NobaraEspRanks#rankKey}.
 *
 * <p>Players are classified by their vessel's roster grade string; mobs are classified by max
 * health thresholds: ≥100 Special Grade, ≥40 Grade 1, ≥20 Grade 2, else Grade 3. Boundary values
 * are inclusive on the upper thresholds.
 */
final class NobaraEspRanksTest {
	@Test
	void playerWithVesselGradeReturnsVesselKey() {
		String key = NobaraEspRanks.rankKey(true, "grade_1", 20.0f);
		assertEquals("grade_1", key);
	}

	@Test
	void playerWithDifferentVesselGradeReturnsThatKey() {
		String key = NobaraEspRanks.rankKey(true, "special_grade", 105.0f);
		assertEquals("special_grade", key);
	}

	@Test
	void playerWithoutVesselReturnsCivilian() {
		String key = NobaraEspRanks.rankKey(true, null, 20.0f);
		assertEquals("esp.jujutsumod.rank.civilian", key);
	}

	@Test
	void mobWithHundredPlusHealthIsSpecialGrade() {
		assertEquals("esp.jujutsumod.rank.special_grade", NobaraEspRanks.rankKey(false, null, 105.0f));
	}

	@Test
	void mobWithExactlyOneHundredHealthIsSpecialGrade() {
		assertEquals("esp.jujutsumod.rank.special_grade", NobaraEspRanks.rankKey(false, null, 100.0f));
	}

	@Test
	void mobWithNinetyNineHealthIsNotSpecialGrade() {
		String key = NobaraEspRanks.rankKey(false, null, 99.0f);
		assertEquals("esp.jujutsumod.rank.rank1", key);
	}

	@Test
	void mobWithExactlyFortyHealthIsRank1() {
		assertEquals("esp.jujutsumod.rank.rank1", NobaraEspRanks.rankKey(false, null, 40.0f));
	}

	@Test
	void mobWithThirtyNineHealthIsNotRank1() {
		assertEquals("esp.jujutsumod.rank.rank2", NobaraEspRanks.rankKey(false, null, 39.0f));
	}

	@Test
	void mobWithExactlyTwentyHealthIsRank2() {
		assertEquals("esp.jujutsumod.rank.rank2", NobaraEspRanks.rankKey(false, null, 20.0f));
	}

	@Test
	void mobWithNineteenHealthIsNotRank2() {
		assertEquals("esp.jujutsumod.rank.rank3", NobaraEspRanks.rankKey(false, null, 19.0f));
	}

	@Test
	void mobWithFiveHealthIsRank3() {
		assertEquals("esp.jujutsumod.rank.rank3", NobaraEspRanks.rankKey(false, null, 5.0f));
	}

	@Test
	void mobWithHalfHealthIsRank3() {
		assertEquals("esp.jujutsumod.rank.rank3", NobaraEspRanks.rankKey(false, null, 0.5f));
	}
}
