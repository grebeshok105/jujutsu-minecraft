package jujutsu.mod.client.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tick-by-tick contract of the quick-selector key: a press that comes up inside the hold threshold is a
 * cycle, a press that reaches it opens the strip and cancels that press's cycle, and neither can be
 * earned twice by holding the key longer.
 */
final class SelectorGestureTest {
	@Test
	void tapShorterThanTheThresholdCyclesOnRelease() {
		SelectorGesture gesture = new SelectorGesture();
		gesture.press();
		for (int tick = 1; tick < SelectorGesture.HOLD_THRESHOLD_TICKS; tick++) {
			assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld(),
					"tick " + tick + " is inside the threshold and must not open the strip");
		}
		assertEquals(SelectorGesture.Action.CYCLE, gesture.release());
		assertFalse(gesture.isOpen());
	}

	@Test
	void holdingToTheThresholdOpensExactlyOnce() {
		SelectorGesture gesture = new SelectorGesture();
		gesture.press();
		for (int tick = 0; tick < SelectorGesture.HOLD_THRESHOLD_TICKS - 1; tick++) {
			assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld(), "before the threshold: silent");
		}
		assertEquals(SelectorGesture.Action.OPEN, gesture.tickHeld(), "the threshold tick opens the strip");
		assertTrue(gesture.isOpen());
		assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld(), "further ticks must not re-open it");
		assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld());
	}

	@Test
	void releasingAnOpenedGestureNeverCycles() {
		SelectorGesture gesture = new SelectorGesture();
		gesture.press();
		for (int tick = 0; tick < SelectorGesture.HOLD_THRESHOLD_TICKS; tick++) {
			gesture.tickHeld();
		}
		assertTrue(gesture.isOpen());
		assertEquals(SelectorGesture.Action.NONE, gesture.release(), "the hold cancelled the cycle for this press");
		assertFalse(gesture.isOpen());
	}

	@Test
	void releaseWithoutAPressAndTickWithoutAPressAreSilent() {
		SelectorGesture gesture = new SelectorGesture();
		assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld(), "a tick with no press must not start a hold");
		assertEquals(SelectorGesture.Action.NONE, gesture.release());
		assertFalse(gesture.isOpen());
	}

	/**
	 * One instance serves the whole session, so the tap must still work after an earlier hold — a flag
	 * that survives the release would silently eat every following short press.
	 */
	@Test
	void aTapAfterAnOpenedHoldCyclesAgain() {
		SelectorGesture gesture = new SelectorGesture();
		gesture.press();
		for (int tick = 0; tick < SelectorGesture.HOLD_THRESHOLD_TICKS; tick++) {
			gesture.tickHeld();
		}
		assertEquals(SelectorGesture.Action.NONE, gesture.release());

		gesture.press();
		assertEquals(SelectorGesture.Action.CYCLE, gesture.release());
	}

	@Test
	void resetDropsTheGestureWithoutAnAction() {
		SelectorGesture gesture = new SelectorGesture();
		gesture.press();
		gesture.tickHeld();
		gesture.reset();
		assertFalse(gesture.isOpen());
		assertEquals(SelectorGesture.Action.NONE, gesture.tickHeld(), "after reset a tick is not a hold");
		assertEquals(SelectorGesture.Action.NONE, gesture.release(), "after reset a release is not a cycle");
	}
}
