package jujutsu.mod.client.vfx;

/**
 * The swap's brief silence pauses sound categories globally, and vanilla uses the same switch for the
 * pause menu and for a lost window focus. Every assertion here is about one rule: we may only ever lift a
 * pause we started, and we must always eventually lift it.
 */
public final class VfxSoundDuckTest {
	private VfxSoundDuckTest() {}

	public static void main(String[] args) {
		assertOnlyOurOwnPauseIsEverLifted();
		assertASecondDuckExtendsAndNeverTruncates();
		assertALateCueStillEndsOnTime();
		assertAScreenEndsTheDuckAtOnce();
		System.out.println("VfxSoundDuckTest passed");
	}

	private static void assertOnlyOurOwnPauseIsEverLifted() {
		// The load-bearing one. Vanilla pauses audio for its own reasons; if IDLE ever asked to restore, the
		// channel would resume a menu's silence out from under it.
		assert !VfxSoundDuck.shouldRestore(VfxSoundDuck.State.IDLE, 10_000L, 0L, false)
				: "an idle channel must never resume a pause it did not start";
		assert !VfxSoundDuck.shouldRestore(VfxSoundDuck.State.IDLE, 10_000L, 0L, true)
				: "an open screen must not turn an idle channel into a resume";
		assert VfxSoundDuck.shouldRestore(VfxSoundDuck.State.DUCKED_BY_TODO, 1_200L, 1_200L, false)
				: "our own duck must lift on its deadline";

		assert VfxSoundDuck.canStart(VfxSoundDuck.State.IDLE, false, true)
				: "an ordinary cast in a loaded world must be able to duck";
		assert !VfxSoundDuck.canStart(VfxSoundDuck.State.IDLE, true, true)
				: "a duck must not begin under an open screen, or it would straddle a menu";
		assert !VfxSoundDuck.canStart(VfxSoundDuck.State.IDLE, false, false)
				: "no level means no world audio to step back";
		assert !VfxSoundDuck.canStart(VfxSoundDuck.State.DUCKED_BY_TODO, false, true)
				: "an active duck must extend rather than start again, or it would re-pause and re-arm";
	}

	private static void assertASecondDuckExtendsAndNeverTruncates() {
		assert VfxSoundDuck.extendedDeadline(1_000L, 1_400L) == 1_400L
				: "a later duck must carry the silence further";
		// Assignment instead of a maximum would let a second, shorter duck cut the first one short and lift
		// the silence in the middle of the first swap's landing.
		assert VfxSoundDuck.extendedDeadline(1_000L, 800L) == 1_000L
				: "a shorter overlapping duck must not truncate the one already running";
		assert VfxSoundDuck.extendedDeadline(1_000L, 1_000L) == 1_000L
				: "an identical deadline must be a no-op";
	}

	private static void assertALateCueStillEndsOnTime() {
		long now = 100_000L;
		long onTime = VfxSoundDuck.deadlineMillis(now, 6, 0.0f);
		assert onTime == now + 300L : "six ticks of silence is three hundred milliseconds";
		// Two ticks late means two ticks less silence, so every client in range comes back at once.
		assert VfxSoundDuck.deadlineMillis(now, 6, 2.0f) == onTime - 100L
				: "a late cue must end at the same instant as an on-time one, not later";
		assert VfxSoundDuck.deadlineMillis(now, 6, -3.0f) == onTime
				: "a negative age must not push the deadline into the future";
		assert VfxSoundDuck.deadlineMillis(now, 0, 0.0f) > now
				: "a zero-tick duck must still have a deadline ahead of it, or it could never be lifted";
	}

	private static void assertAScreenEndsTheDuckAtOnce() {
		// Not at the deadline: vanilla is about to take the audio for the menu and must find it untouched.
		assert VfxSoundDuck.shouldRestore(VfxSoundDuck.State.DUCKED_BY_TODO, 1_000L, 5_000L, true)
				: "opening a screen must lift our silence immediately";
		assert !VfxSoundDuck.shouldRestore(VfxSoundDuck.State.DUCKED_BY_TODO, 1_000L, 5_000L, false)
				: "a live duck with no screen must run to its deadline";
	}
}
