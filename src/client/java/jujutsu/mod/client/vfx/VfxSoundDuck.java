package jujutsu.mod.client.vfx;

/**
 * The arithmetic and the ownership rule behind the swap's brief silence, kept free of Minecraft types so
 * it can actually be tested rather than only asserted about.
 *
 * <p>Pausing and resuming sound categories is a global operation on the client's sound manager, shared
 * with vanilla — the pause menu and a lost window focus both use it. So the rule this class exists to
 * express is the one that keeps that sharing safe: <b>only ever undo a pause we started ourselves.</b>
 * A resume from {@link State#IDLE} would be us un-pausing somebody else's silence.
 */
public final class VfxSoundDuck {
	private static final long MILLIS_PER_TICK = 50L;

	/** Who owns the current pause. Nothing may be resumed from IDLE. */
	public enum State {
		IDLE,
		DUCKED_BY_TODO
	}

	private VfxSoundDuck() {}

	/**
	 * When a duck started now should end, back-dated by how late its cue arrived so every client's silence
	 * lifts at the same instant regardless of latency.
	 */
	public static long deadlineMillis(long nowMillis, int durationTicks, float initialAgeTicks) {
		long startedAt = nowMillis - Math.round(Math.max(0.0f, initialAgeTicks) * MILLIS_PER_TICK);
		return startedAt + Math.max(1, durationTicks) * MILLIS_PER_TICK;
	}

	/**
	 * A second duck extends the window and never truncates it. Taking the candidate outright would let a
	 * later but shorter duck cut the first one short and lift the silence early.
	 */
	public static long extendedDeadline(long currentDeadlineMillis, long candidateDeadlineMillis) {
		return Math.max(currentDeadlineMillis, candidateDeadlineMillis);
	}

	/**
	 * A screen ends the duck at once rather than at the deadline: vanilla is about to take over the audio
	 * for the menu, and it must find it in the state it left it.
	 */
	public static boolean shouldRestore(State state, long nowMillis, long deadlineMillis, boolean screenOpen) {
		return state == State.DUCKED_BY_TODO && (screenOpen || nowMillis >= deadlineMillis);
	}

	/** Refuse to start while a screen is open, so a duck can never straddle a menu it did not begin outside. */
	public static boolean canStart(State state, boolean screenOpen, boolean levelLoaded) {
		return state == State.IDLE && !screenOpen && levelLoaded;
	}
}
