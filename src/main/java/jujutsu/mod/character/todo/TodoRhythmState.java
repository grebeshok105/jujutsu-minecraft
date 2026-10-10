package jujutsu.mod.character.todo;

import java.util.List;

/** Immutable server-side Boogie Rhythm snapshot owned by {@link TodoTransientState}. */
public record TodoRhythmState(
		int points,
		SwapKind lastKind,
		List<SwapKind> recent,
		long lastSwapGameTime,
		boolean peakArmed,
		long revisedUntilGameTime,
		int autoSwapsUsed,
		List<PendingAutoSwap> pending) {

	/** A missing rhythm entry is represented by this immutable all-zero value. */
	public static final TodoRhythmState ZERO = new TodoRhythmState(
			0, null, List.of(), 0L, false, 0L, 0, List.of());

	public TodoRhythmState {
		recent = recent == null ? List.of() : List.copyOf(recent);
		pending = pending == null ? List.of() : List.copyOf(pending);
		points = Math.max(0, points);
		autoSwapsUsed = Math.max(0, autoSwapsUsed);
	}

	/** Beat 0..4 derived only from the authoritative point total. */
	public int beat() {
		if (points >= TodoProfile.RHYTHM_PEAK_POINTS) {
			return 4;
		}
		if (points >= TodoProfile.RHYTHM_BEAT3_POINTS) {
			return 3;
		}
		if (points >= TodoProfile.RHYTHM_BEAT2_POINTS) {
			return 2;
		}
		if (points >= TodoProfile.RHYTHM_BEAT1_POINTS) {
			return 1;
		}
		return 0;
	}

	public boolean revisedAt(long gameTime) {
		return revisedUntilGameTime > gameTime;
	}
	/** Beat at a server tick: an open Revised window is always presented as Peak. */
	public int beatAt(long gameTime) {
		return revisedAt(gameTime) ? 4 : beat();
	}

	public TodoRhythmState withPoints(int nextPoints) {
		return new TodoRhythmState(nextPoints, lastKind, recent, lastSwapGameTime, peakArmed,
				revisedUntilGameTime, autoSwapsUsed, pending);
	}

	public TodoRhythmState withSwap(SwapKind nextKind, List<SwapKind> nextRecent, long gameTime,
			int nextPoints, boolean nextPeakArmed) {
		return new TodoRhythmState(nextPoints, nextKind, nextRecent, gameTime, nextPeakArmed,
				revisedUntilGameTime, autoSwapsUsed, pending);
	}

	public TodoRhythmState withRevised(long untilGameTime, int nextAutoSwapsUsed,
			List<PendingAutoSwap> nextPending) {
		return new TodoRhythmState(points, lastKind, recent, lastSwapGameTime, false, untilGameTime,
			nextAutoSwapsUsed, nextPending);
	}

	public TodoRhythmState withAutoSwaps(int nextAutoSwapsUsed, List<PendingAutoSwap> nextPending) {
		return new TodoRhythmState(points, lastKind, recent, lastSwapGameTime, peakArmed,
			revisedUntilGameTime, nextAutoSwapsUsed, nextPending);
	}

	public TodoRhythmState clearRevised() {
		return ZERO;
	}
}
