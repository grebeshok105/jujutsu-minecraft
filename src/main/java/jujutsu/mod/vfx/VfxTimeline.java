package jujutsu.mod.vfx;

public final class VfxTimeline {
	private static final float OPENING_BEAT_TICKS = 2.0f;
	private static final double MILLIS_PER_TICK = 50.0;
	private static final double NANOS_PER_TICK = 50_000_000.0;

	private VfxTimeline() {}

	public static float ageTicks(VfxCue cue, long gameTime, float partialTick) {
		return Math.max(0.0f, gameTime - cue.startGameTime() + partialTick);
	}

	public static boolean isExpired(VfxCue cue, long gameTime, int durationTicks) {
		return ageTicks(cue, gameTime, 0.0f) >= durationTicks;
	}

	public static boolean isOpeningBeat(float ageTicks) {
		return clampedAge(ageTicks) < OPENING_BEAT_TICKS;
	}

	public static long startedAtMillis(long nowMillis, float ageTicks) {
		return nowMillis - Math.round(clampedAge(ageTicks) * MILLIS_PER_TICK);
	}

	public static long startedAtNanos(long nowNanos, float ageTicks) {
		return nowNanos - Math.round(clampedAge(ageTicks) * NANOS_PER_TICK);
	}

	public static boolean shouldExtendRealtimeWindow(
			long currentStartedAtMillis,
			int currentDurationMillis,
			long candidateStartedAtMillis,
			int candidateDurationMillis,
			long nowMillis
	) {
		long currentEndsAtMillis = currentStartedAtMillis + Math.max(0, currentDurationMillis);
		long candidateEndsAtMillis = candidateStartedAtMillis + Math.max(1, candidateDurationMillis);
		return candidateEndsAtMillis > nowMillis && candidateEndsAtMillis > currentEndsAtMillis;
	}

	private static float clampedAge(float ageTicks) {
		return Math.max(0.0f, ageTicks);
	}
}
