package jujutsu.mod.cursedincident.policy;

import java.util.ArrayList;
import java.util.List;

import jujutsu.mod.cursedincident.IncidentStage;

/** Pure logical-age policy for the five-stage incident ladder. */
public final class StagePolicy {
	/** Base thresholds: 0, 2, 5, 9 and 12 in-game days at 24,000 ticks/day. */
	public static final long[] STAGE_AGE_TICKS = {0L, 48_000L, 120_000L, 216_000L, 288_000L};

	private StagePolicy() {
	}

	/**
	 * Returns the stage reached at an age. The multiplier scales thresholds (a smaller
	 * value therefore escalates faster). Invalid multipliers use the neutral value.
	 */
	public static IncidentStage stageForAge(long ageTicks, double escalationSpeedMul) {
		long age = Math.max(0L, ageTicks);
		double multiplier = validMultiplier(escalationSpeedMul);
		IncidentStage result = IncidentStage.INITIAL;
		for (int i = 1; i < STAGE_AGE_TICKS.length; i++) {
			if ((double) age >= STAGE_AGE_TICKS[i] * multiplier) {
				result = IncidentStage.values()[i];
			} else {
				break;
			}
		}
		return result;
	}

	/** Returns the scaled threshold for one stage. */
	public static long thresholdFor(IncidentStage stage, double escalationSpeedMul) {
		if (stage == null) {
			return STAGE_AGE_TICKS[0];
		}
		int index = Math.max(0, Math.min(stage.ordinal(), STAGE_AGE_TICKS.length - 1));
		// ceil, not round: the requested age must satisfy stageForAge's `age >= threshold`
		// check. round() can land below the true threshold when 48000·speed isn't an exact
		// double (e.g. 48000·0.77 = 36960.000000000004 → round 36960 < threshold → the
		// spawn-time transition silently never fires and the record stays INITIAL).
		return (long) Math.ceil(STAGE_AGE_TICKS[index] * validMultiplier(escalationSpeedMul));
	}

	/**
	 * Lists every forward transition crossed between two logical ages. Transitions are
	 * never skipped and SCAR is not represented by this ladder.
	 */
	public static List<IncidentStage> transitionsBetween(
			IncidentStage from, long ageBefore, long ageAfter, double escalationSpeedMul) {
		IncidentStage current = from == null ? IncidentStage.INITIAL : from;
		if (ageAfter <= ageBefore || current.isTerminal()) {
			return List.of();
		}
		IncidentStage target = stageForAge(ageAfter, escalationSpeedMul);
		if (target.ordinal() <= current.ordinal()) {
			return List.of();
		}
		List<IncidentStage> transitions = new ArrayList<>(target.ordinal() - current.ordinal());
		while (current.ordinal() < target.ordinal()) {
			current = current.next();
			transitions.add(current);
		}
		return List.copyOf(transitions);
	}

	private static double validMultiplier(double value) {
		return Double.isFinite(value) && value > 0.0 ? value : 1.0;
	}
}
