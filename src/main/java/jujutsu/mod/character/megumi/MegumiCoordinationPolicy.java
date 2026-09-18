package jujutsu.mod.character.megumi;

import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import net.minecraft.util.RandomSource;

/**
 * The pure half of autonomous target choice (issue #107): how much a candidate is worth to one
 * body, given the shared combat context. Everything here is a weight — there is no veto path
 * (§11/§12): a worse idea scores lower, it is never forbidden.
 *
 * <p>Three rules sit next to the score, not inside it: hysteresis (a challenger must beat the
 * standing mark by {@link MegumiShikigamiProfile#COORD_HYSTERESIS}, so picks do not thrash), the
 * spread rule (when free candidates exist, an occupied best yields to the best free one — unless
 * the occupied one is claimed by an ally's intent or is actively threatening the pack, which is
 * exactly when piling on is correct), and the autonomy band (outside the work radius a body holds
 * its mark, past the return radius a self-placed mark drops; manual marks are exempt).
 */
public final class MegumiCoordinationPolicy {
	private MegumiCoordinationPolicy() {}

	/** What a body may do about its mark at this distance from the owner (spec §16). */
	public enum BandAction {
		/** Inside the work radius: free to take a new mark. */
		ASSIGNABLE,
		/** In the hysteresis band: keep the current mark, take no new one. */
		HOLD,
		/** Past the return radius: a self-placed mark drops so the body walks home. */
		DROP
	}

	/**
	 * The band decision on plain numbers — the part worth testing without a level.
	 * {@code selfPlaced} is true for AUTONOMOUS and RETALIATION marks; a MANUAL sic is the owner's
	 * order and ignores the band entirely (R18).
	 */
	public static BandAction bandAction(double distanceToOwner, boolean selfPlaced) {
		if (distanceToOwner <= MegumiShikigamiProfile.AUTONOMY_RADIUS) {
			return BandAction.ASSIGNABLE;
		}
		if (distanceToOwner > MegumiShikigamiProfile.RETURN_RADIUS && selfPlaced) {
			return BandAction.DROP;
		}
		return BandAction.HOLD;
	}

	/**
	 * One candidate's worth to one body. Zero means "not choosable this scan" (dead, ineligible,
	 * unseen, or outside the autonomy radius — R20); every positive score is a reasonable pick.
	 */
	public static double score(CandidateFacts facts, RandomSource random) {
		if (!facts.alive() || !facts.eligible() || !facts.hasLineOfSight()
				|| facts.distanceToOwner() > MegumiShikigamiProfile.AUTONOMY_RADIUS) {
			return 0.0;
		}
		double value = 1.0;
		value -= facts.distanceToBody() * MegumiShikigamiProfile.COORD_DISTANCE_WEIGHT;
		value += Math.min(facts.maxHealth() / 20.0, 2.0) * MegumiShikigamiProfile.COORD_DANGER_WEIGHT;
		if (facts.soaked()) {
			value += MegumiShikigamiProfile.COORD_SOAKED_BONUS;
		}
		if (facts.held()) {
			value += MegumiShikigamiProfile.COORD_HELD_BONUS;
		}
		if (facts.intentTarget()) {
			value += MegumiShikigamiProfile.COORD_INTENT_BONUS;
		}
		if (facts.threatensAlly()) {
			value *= MegumiShikigamiProfile.COORD_ALLY_THREAT_FACTOR;
		}
		if (facts.occupied()) {
			value *= MegumiShikigamiProfile.COORD_OCCUPANCY_FACTOR;
		}
		return value + random.nextDouble() * MegumiShikigamiProfile.COORD_JITTER;
	}

	/**
	 * Hysteresis (§10, R4): the challenger must beat the standing mark by the factor, not merely
	 * edge it out — otherwise two near-equal candidates would swap every scan.
	 */
	public static boolean beatsWithHysteresis(double challenger, double current) {
		return challenger > current * MegumiShikigamiProfile.COORD_HYSTERESIS;
	}

	/**
	 * The spread rule (§3, R3): returns the index of the candidate to assign, or -1 when nothing
	 * scores. The best-scoring candidate wins outright when it is free, claimed by an ally's
	 * intent, or threatening the pack — piling on is correct there. When the best is merely
	 * occupied and a free candidate exists, the best free one wins instead, so a crowd is
	 * distributed rather than mobbed.
	 */
	public static int pick(int count, IntToDoubleFunction score,
			IntPredicate occupied, IntPredicate intentOrThreat) {
		int best = -1;
		int bestFree = -1;
		double bestScore = 0.0;
		double bestFreeScore = 0.0;
		for (int i = 0; i < count; i++) {
			double s = score.applyAsDouble(i);
			if (s <= 0.0) {
				continue;
			}
			if (s > bestScore) {
				bestScore = s;
				best = i;
			}
			if (!occupied.test(i) && s > bestFreeScore) {
				bestFreeScore = s;
				bestFree = i;
			}
		}
		if (best < 0) {
			return -1;
		}
		if (occupied.test(best) && !intentOrThreat.test(best) && bestFree >= 0) {
			return bestFree;
		}
		return best;
	}

	/**
	 * The candidate facts a score reads, flattened to primitives so the policy is testable without
	 * a level. {@code occupied} means another body already marks it; {@code intentTarget} means an
	 * ally's committed action (a toad windup, a dive, a jet) points at it.
	 */
	public record CandidateFacts(
			boolean alive,
			boolean eligible,
			boolean hasLineOfSight,
			double distanceToOwner,
			double distanceToBody,
			double maxHealth,
			boolean soaked,
			boolean held,
			boolean intentTarget,
			boolean threatensAlly,
			boolean occupied) {}

}
