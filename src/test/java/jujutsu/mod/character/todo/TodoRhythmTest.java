package jujutsu.mod.character.todo;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure Boogie Rhythm contracts.
 *
 * Run: ./gradlew.bat test --tests "*TodoRhythm*"
 * Expected: BUILD SUCCESSFUL
 * Red: before the rhythm state/runtime exists, the command fails to compile because TodoRhythmState,
 * TodoRhythmRuntime, and TodoCooldownPolicy are missing.
 */

public final class TodoRhythmTest {
	public TodoRhythmTest() {}

	@Test
	void spamDoesNotBuildRhythmTest() {
		spamDoesNotBuildRhythm();
	}

	@Test
	void varietyReachesPeakTest() {
		varietyReachesPeak();
	}

	@Test
	void thresholdsAndPeakArmingTest() {
		thresholdsAndPeakArming();
	}

	@Test
	void decayHonorsGraceAndArmedPeakTest() {
		decayHonorsGraceAndArmedPeak();
	}

	@Test
	void revisedExpiryClearsPendingTest() {
		revisedExpiryClearsPending();
	}

	@Test
	void revisedCooldownMathTest() {
		revisedCooldownMath();
	}

	@Test
	void momentumScalesOnlyStaggerAndCueTest() {
		momentumScalesOnlyStaggerAndCue();
	}

	@Test
	void groundPredicateRequiresAnySolidBlockTest() {
		groundPredicateRequiresAnySolidBlock();
	}

	public static void main(String[] args) {
		spamDoesNotBuildRhythm();
		varietyReachesPeak();
		thresholdsAndPeakArming();
		decayHonorsGraceAndArmedPeak();
		revisedExpiryClearsPending();
		revisedCooldownMath();
		momentumScalesOnlyStaggerAndCue();
		groundPredicateRequiresAnySolidBlock();
		System.out.println("TodoRhythmTest passed");
	}

	private static void spamDoesNotBuildRhythm() {
		TodoRhythmState state = TodoRhythmState.ZERO;
		int[] points = new int[4];
		for (int index = 0; index < points.length; index++) {
			state = TodoRhythmRuntime.advance(state, SwapKind.AIMED, index);
			points[index] = state.points();
		}
		assert points[0] == 2 : points[0];
		assert points[1] == 2 : points[1];
		assert points[2] == 2 : points[2];
		assert points[3] == 2 : points[3];
		assert state.beat() == 0 : state.beat();
	}

	private static void varietyReachesPeak() {
		TodoRhythmState state = TodoRhythmState.ZERO;
		SwapKind[] kinds = {SwapKind.AIMED, SwapKind.STONE_SELF, SwapKind.PAIR, SwapKind.TRIPLE, SwapKind.AIMED};
		int[] expected = {2, 4, 6, 8, 10};
		for (int index = 0; index < kinds.length; index++) {
			state = TodoRhythmRuntime.advance(state, kinds[index], index);
			assert state.points() == expected[index] : state.points();
		}
		assert state.beat() == 4 : state.beat();
		assert state.peakArmed() : "variety sequence must arm Peak";
		assert state.recent().equals(List.of(SwapKind.AIMED, SwapKind.TRIPLE, SwapKind.PAIR)) : state.recent();
	}

	private static void thresholdsAndPeakArming() {
		TodoRhythmState state = TodoRhythmState.ZERO;
		state = state.withPoints(TodoProfile.RHYTHM_BEAT1_POINTS - 1);
		assert state.beat() == 0;
		state = state.withPoints(TodoProfile.RHYTHM_BEAT1_POINTS);
		assert state.beat() == 1;
		state = state.withPoints(TodoProfile.RHYTHM_BEAT2_POINTS);
		assert state.beat() == 2;
		state = state.withPoints(TodoProfile.RHYTHM_BEAT3_POINTS);
		assert state.beat() == 3;
		state = state.withPoints(TodoProfile.RHYTHM_PEAK_POINTS);
		assert state.beat() == 4;
		assert !state.peakArmed() : "points alone do not consume/arm without a swap transition";
	}

	private static void decayHonorsGraceAndArmedPeak() {
		TodoRhythmState state = new TodoRhythmState(5, SwapKind.AIMED, List.of(SwapKind.AIMED), 100,
				false, 0, 0, List.of());
		assert !TodoRhythmRuntime.shouldDecay(state, 200) : "grace boundary is strict";
		assert TodoRhythmRuntime.shouldDecay(state, 240) : "first decay is delay + interval";
		TodoRhythmState armed = new TodoRhythmState(10, SwapKind.AIMED, List.of(SwapKind.AIMED), 100,
				true, 0, 0, List.of());
		assert armed.peakArmed();
		assert !TodoRhythmRuntime.shouldDecay(armed, 10_000) : "serverTick skips armed Peak";
	}

	private static void revisedExpiryClearsPending() {
		PendingAutoSwap pending = new PendingAutoSwap(null, 120);
		TodoRhythmState revised = new TodoRhythmState(10, SwapKind.AIMED, List.of(SwapKind.AIMED), 0,
				false, 120, 2, List.of(pending));
		assert revised.revisedAt(119);
		assert !revised.revisedAt(120);
		TodoRhythmState expired = revised.clearRevised();
		assert expired.points() == 0;
		assert expired.autoSwapsUsed() == 0;
		assert expired.pending().isEmpty();
		assert expired.revisedUntilGameTime() == 0;
	}

	private static void revisedCooldownMath() {
		assert TodoCooldownPolicy.effectiveTicks(false, 100) == 100;
		assert TodoCooldownPolicy.effectiveTicks(true, 100) == 25;
		assert TodoCooldownPolicy.effectiveTicks(true, 20) == TodoProfile.REVISED_MIN_COOLDOWN_TICKS;
	}

	private static void momentumScalesOnlyStaggerAndCue() {
		assert TodoSwapMomentum.staggerTicks(0) == 8;
		assert TodoSwapMomentum.staggerTicks(3) == 14;
		assert TodoSwapMomentum.staggerTicks(4) == 16;
		assert TodoSwapMomentum.staggerTicks(99) == 16;
		assert TodoSwapMomentum.cueIntensity(0) == 1;
		assert TodoSwapMomentum.cueIntensity(4) == 5;
		assert TodoProfile.SWAP_MOMENTUM_DAMAGE_MULTIPLIER == 1.25;
	}

	private static void groundPredicateRequiresAnySolidBlock() {
		assert TodoRhythmRuntime.hasGroundWithin(new boolean[] {false, false, true, false});
		assert TodoRhythmRuntime.hasGroundWithin(new boolean[] {true});
		assert !TodoRhythmRuntime.hasGroundWithin(new boolean[] {false, false, false, false});
		assert !TodoRhythmRuntime.hasGroundWithin(null);
	}
}
