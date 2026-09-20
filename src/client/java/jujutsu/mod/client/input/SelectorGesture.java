package jujutsu.mod.client.input;

/**
 * The quick-selector key's whole gesture, as one pure state machine: a press that comes up before the
 * hold threshold is a tap and cycles to the next available shikigami, a press that stays down for
 * {@link #HOLD_THRESHOLD_TICKS} opens the visual strip, and coming up after that only closes it — a hold
 * never also earns the tap's cycle.
 *
 * <p>Free of Minecraft types on purpose: the timing rule is the part worth testing, and it can be tested
 * tick by tick here instead of by driving a client. The caller owns the key — it presses on the falling
 * edge, ticks once per client tick while the key reads down, and releases once on the rising edge.
 *
 * <p>The selection itself is deliberately not touched here: the spec resolves a press on release, so
 * starting a hold must never advance anything before the player commits to it.
 */
public final class SelectorGesture {
	public enum Action {
		/** Nothing to do for this call. */
		NONE,
		/** The press was a tap: advance to the next available shikigami. */
		CYCLE,
		/** The press crossed the hold threshold: open the selector strip. */
		OPEN
	}

	/** Client ticks the key must stay down to count as a hold — 4 ticks is about 200 ms at 20 TPS. */
	public static final int HOLD_THRESHOLD_TICKS = 4;

	/** -1 while the key is up; otherwise how many client ticks it has been held so far. */
	private int heldTicks = -1;

	/** Set by the threshold tick and cleared by release or reset; keeps {@link Action#OPEN} one-shot. */
	private boolean opened;

	public SelectorGesture() {}

	/** The key went down: start the hold clock, and no selection change yet. */
	public void press() {
		heldTicks = 0;
		opened = false;
	}

	/**
	 * One client tick while the key reads down. Returns {@link Action#OPEN} exactly once, on the tick that
	 * reaches {@link #HOLD_THRESHOLD_TICKS}; later ticks of the same hold are silent, or the strip would be
	 * re-opened under the player every tick.
	 */
	public Action tickHeld() {
		if (heldTicks < 0) {
			return Action.NONE;
		}
		heldTicks++;
		if (!opened && heldTicks >= HOLD_THRESHOLD_TICKS) {
			opened = true;
			return Action.OPEN;
		}
		return Action.NONE;
	}

	/**
	 * The key came up. A press that never opened the strip was a tap and cycles; one that did open it only
	 * closes, because the hold cancelled that press's cycle. Either way the gesture is done afterwards, so
	 * the next press starts clean.
	 */
	public Action release() {
		Action action = heldTicks >= 0 && !opened ? Action.CYCLE : Action.NONE;
		reset();
		return action;
	}

	/** Whether this press opened the strip and is still waiting for its release to close it. */
	public boolean isOpen() {
		return opened;
	}

	/**
	 * Drops the gesture without producing an action — for when the key's owner disappears mid-press (the
	 * player left the world) and a release may never arrive.
	 */
	public void reset() {
		heldTicks = -1;
		opened = false;
	}
}
