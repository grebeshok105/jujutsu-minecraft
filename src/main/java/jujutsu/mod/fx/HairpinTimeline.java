package jujutsu.mod.fx;

public final class HairpinTimeline {
	public static final int PREP_FREEZE_MS = 180;
	public static final int HAMMER_SNAP_MS = 60;
	public static final int NAIL_IGNITION_MS = 320;
	public static final int HAIRPIN_BLOOM_MS = 340;
	public static final int AFTERGLOW_MS = 900;

	private HairpinTimeline() {}

	public enum Phase {
		PREP_FREEZE,
		HAMMER_SNAP,
		NAIL_IGNITION,
		HAIRPIN_BLOOM,
		AFTERGLOW,
		DONE
	}

	public static int totalDurationMillis() {
		return PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS + HAIRPIN_BLOOM_MS + AFTERGLOW_MS;
	}

	public static long elapsedMillisFromGameTime(long startGameTime, long currentGameTime) {
		return Math.max(0L, currentGameTime - startGameTime) * 50L;
	}

	public static Phase phaseAtGameTime(long startGameTime, long currentGameTime) {
		return phaseAt(elapsedMillisFromGameTime(startGameTime, currentGameTime));
	}

	public static Phase phaseAt(long elapsedMillis) {
		if (elapsedMillis < 0) {
			return Phase.PREP_FREEZE;
		}

		long cursor = elapsedMillis;
		if (cursor < PREP_FREEZE_MS) {
			return Phase.PREP_FREEZE;
		}
		cursor -= PREP_FREEZE_MS;
		if (cursor < HAMMER_SNAP_MS) {
			return Phase.HAMMER_SNAP;
		}
		cursor -= HAMMER_SNAP_MS;
		if (cursor < NAIL_IGNITION_MS) {
			return Phase.NAIL_IGNITION;
		}
		cursor -= NAIL_IGNITION_MS;
		if (cursor < HAIRPIN_BLOOM_MS) {
			return Phase.HAIRPIN_BLOOM;
		}
		cursor -= HAIRPIN_BLOOM_MS;
		if (cursor < AFTERGLOW_MS) {
			return Phase.AFTERGLOW;
		}
		return Phase.DONE;
	}

	public static float progressInPhase(long elapsedMillis) {
		Phase phase = phaseAt(elapsedMillis);
		if (phase == Phase.DONE) {
			return 1.0f;
		}

		long start = phaseStartMillis(phase);
		int duration = phaseDurationMillis(phase);
		if (duration <= 0) {
			return 1.0f;
		}

		float progress = (elapsedMillis - start) / (float) duration;
		return Math.max(0.0f, Math.min(1.0f, progress));
	}

	public static long phaseStartMillis(Phase phase) {
		return switch (phase) {
			case PREP_FREEZE -> 0L;
			case HAMMER_SNAP -> PREP_FREEZE_MS;
			case NAIL_IGNITION -> PREP_FREEZE_MS + HAMMER_SNAP_MS;
			case HAIRPIN_BLOOM -> PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS;
			case AFTERGLOW -> PREP_FREEZE_MS + HAMMER_SNAP_MS + NAIL_IGNITION_MS + HAIRPIN_BLOOM_MS;
			case DONE -> totalDurationMillis();
		};
	}

	public static int phaseDurationMillis(Phase phase) {
		return switch (phase) {
			case PREP_FREEZE -> PREP_FREEZE_MS;
			case HAMMER_SNAP -> HAMMER_SNAP_MS;
			case NAIL_IGNITION -> NAIL_IGNITION_MS;
			case HAIRPIN_BLOOM -> HAIRPIN_BLOOM_MS;
			case AFTERGLOW -> AFTERGLOW_MS;
			case DONE -> 0;
		};
	}
}
