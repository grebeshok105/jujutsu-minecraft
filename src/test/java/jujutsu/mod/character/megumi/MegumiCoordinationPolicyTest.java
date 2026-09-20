package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * The pure half of autonomous target choice (issue #107): score ordering, the spread rule,
 * hysteresis, the autonomy band and jitter — all on primitives, no level. The entity-shaped half
 * (marks actually landing on bodies) lives in {@code MegumiAutonomyGameTests}.
 */
class MegumiCoordinationPolicyTest {
	private static final RandomSource RANDOM = RandomSource.create(42L);

	private static MegumiCoordinationPolicy.CandidateFacts facts(
			double distToOwner, double distToBody, double maxHealth) {
		return new MegumiCoordinationPolicy.CandidateFacts(true, true, true,
				distToOwner, distToBody, maxHealth, false, false, false, false, false);
	}

	@Test
	void aDeadIneligibleUnseenOrOutOfBandCandidateScoresZero() {
		MegumiCoordinationPolicy.CandidateFacts base = facts(10.0, 5.0, 20.0);
		assertEquals(0.0, MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(false, true, true, 10.0, 5.0, 20.0,
						false, false, false, false, false), RANDOM), "dead");
		assertEquals(0.0, MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, false, true, 10.0, 5.0, 20.0,
						false, false, false, false, false), RANDOM), "ineligible");
		assertEquals(0.0, MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, false, 10.0, 5.0, 20.0,
						false, false, false, false, false), RANDOM), "no line of sight");
		assertEquals(0.0, MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, true,
						MegumiShikigamiProfile.AUTONOMY_RADIUS + 0.01, 5.0, 20.0,
						false, false, false, false, false), RANDOM),
				"one step past the autonomy radius");
		assertTrue(MegumiCoordinationPolicy.score(base, RANDOM) > 0.0, "the plain candidate scores");
	}

	@Test
	void nearerAndStrongerCandidatesScoreHigher() {
		double near = MegumiCoordinationPolicy.score(facts(10.0, 4.0, 20.0), RANDOM);
		double far = MegumiCoordinationPolicy.score(facts(10.0, 30.0, 20.0), RANDOM);
		assertTrue(near > far, "nearer beats farther");
		double weak = MegumiCoordinationPolicy.score(facts(10.0, 5.0, 20.0), RANDOM);
		double strong = MegumiCoordinationPolicy.score(facts(10.0, 5.0, 100.0), RANDOM);
		assertTrue(strong > weak, "danger weighs more (§3)");
	}

	@Test
	void allyThreatBeatsPlain() {
		double plain = MegumiCoordinationPolicy.score(facts(10.0, 5.0, 20.0), RANDOM);
		double ally = MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, true, 10.0, 5.0, 20.0,
						false, false, false, true, false), RANDOM);
		assertTrue(ally > plain, "ally threat raises weight (§14)");
		// §13's owner-threat weight was removed: the retaliation pass marks the owner's aggressor
		// on every body before the coordinator runs, so a score term could never decide a pick.
	}

	@Test
	void stateMatchAndIntentRaiseWeight() {
		double plain = MegumiCoordinationPolicy.score(facts(10.0, 5.0, 20.0), RANDOM);
		double soaked = MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, true, 10.0, 5.0, 20.0,
						true, false, false, false, false), RANDOM);
		double intent = MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, true, 10.0, 5.0, 20.0,
						false, false, true, false, false), RANDOM);
		assertTrue(soaked > plain, "a soaked victim is an opening (§8)");
		assertTrue(intent > plain, "an ally's committed action invites the pile-on (§11)");
	}

	@Test
	void occupancyDeWeightsButNeverZeroes() {
		double free = MegumiCoordinationPolicy.score(facts(10.0, 5.0, 20.0), RANDOM);
		double occupied = MegumiCoordinationPolicy.score(
				new MegumiCoordinationPolicy.CandidateFacts(true, true, true, 10.0, 5.0, 20.0,
						false, false, false, false, true), RANDOM);
		assertTrue(occupied > 0.0, "occupied is still choosable — soft coordination, no veto (R11)");
		assertTrue(occupied < free, "occupied scores lower");
	}

	@Test
	void hysteresisRequiresAMargin() {
		assertFalse(MegumiCoordinationPolicy.beatsWithHysteresis(1.2, 1.0),
				"a 20% edge does not switch the mark");
		assertTrue(MegumiCoordinationPolicy.beatsWithHysteresis(1.3, 1.0),
				"a 30% edge does");
	}

	@Test
	void theSpreadRulePrefersAFreeCandidate() {
		// Two candidates: index 0 scores higher but is occupied; index 1 is free.
		List<String> candidates = List.of("occupied-best", "free");
		int pick = MegumiCoordinationPolicy.pick(candidates.size(),
				i -> i == 0 ? 2.0 : 1.0,
				i -> i == 0,
				i -> false);
		assertEquals(1, pick, "the best free candidate wins over an occupied best (R3)");
	}

	@Test
	void theSpreadRuleYieldsToIntentAndThreat() {
		// Occupied best that is ALSO an ally's intent target: piling on is correct, not spread.
		int pick = MegumiCoordinationPolicy.pick(2,
				i -> i == 0 ? 2.0 : 1.0,
				i -> i == 0,
				i -> i == 0);
		assertEquals(0, pick, "an intent target keeps the pile-on (R10)");
	}

	@Test
	void theSpreadRuleKeepsTheOccupiedBestWhenNothingIsFree() {
		int pick = MegumiCoordinationPolicy.pick(2,
				i -> i == 0 ? 2.0 : 1.0,
				i -> true,
				i -> false);
		assertEquals(0, pick, "no free candidate → the best wins anyway");
	}

	@Test
	void pickReturnsMinusOneWhenNothingScores() {
		assertEquals(-1, MegumiCoordinationPolicy.pick(3, i -> 0.0, i -> false, i -> false));
	}

	@Test
	void theAutonomyBand() {
		assertEquals(MegumiCoordinationPolicy.BandAction.ASSIGNABLE,
				MegumiCoordinationPolicy.bandAction(10.0, true), "inside the work radius");
		assertEquals(MegumiCoordinationPolicy.BandAction.HOLD,
				MegumiCoordinationPolicy.bandAction(17.0, true), "the band holds a self-placed mark");
		assertEquals(MegumiCoordinationPolicy.BandAction.HOLD,
				MegumiCoordinationPolicy.bandAction(17.0, false), "the band holds an unmarked body");
		assertEquals(MegumiCoordinationPolicy.BandAction.DROP,
				MegumiCoordinationPolicy.bandAction(21.0, true),
				"past the return radius a self-placed mark drops (R17)");
		assertEquals(MegumiCoordinationPolicy.BandAction.HOLD,
				MegumiCoordinationPolicy.bandAction(21.0, false),
				"a manual sic ignores the band entirely (R18)");
	}

	@Test
	void jitterIsBoundedAndSeedable() {
		// R19: the same facts can score differently across seeds, but the jitter never exceeds
		// COORD_JITTER — variety stays inside the set of reasonable picks (§10).
		MegumiCoordinationPolicy.CandidateFacts facts = facts(10.0, 5.0, 20.0);
		double min = Double.MAX_VALUE;
		double max = Double.MIN_VALUE;
		for (long seed = 0; seed < 64; seed++) {
			double s = MegumiCoordinationPolicy.score(facts, RandomSource.create(seed));
			min = Math.min(min, s);
			max = Math.max(max, s);
		}
		assertTrue(max - min <= MegumiShikigamiProfile.COORD_JITTER + 1.0E-9,
				"jitter stays inside its bound");
		assertTrue(max > min, "different seeds produce different scores");
	}
}
