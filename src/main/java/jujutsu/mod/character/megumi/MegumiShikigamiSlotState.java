package jujutsu.mod.character.megumi;

/**
 * What one slot of Megumi's shikigami roster currently is, as the selector's wire state carries it.
 *
 * <p><b>The ordinal is the wire value.</b> The snapshot ships one byte per roster slot and the client
 * reads the constant back by ordinal, so reordering these constants is a protocol break, not a rename.
 *
 * <p>Only {@link #READY}, {@link #SUMMONED} and {@link #COOLDOWN} are producible today. {@link #LOCKED},
 * {@link #DESTROYED} and {@link #TEMPORARY} are part of the value space because the design spec names
 * them as reasons the player must be able to read, and the strip draws a marker for each — but no
 * server fact reaches them yet: there is no unlock grind, no permanent loss and no temporary disable.
 * They are declared here so the wire format and the client's marker table are complete, and so that
 * shipping one later is a state change rather than a protocol change.
 */
public enum MegumiShikigamiSlotState {
	/** Selectable, and nothing of this type is out in the world. */
	READY,
	/** Selectable, and a live pack of this type is out. A marker, never a block. */
	SUMMONED,
	/** Not selectable: this type is still recovering from its last dismissal. */
	COOLDOWN,
	/** Not selectable: not unlocked or tamed yet. Unreachable today. */
	LOCKED,
	/** Not selectable: permanently destroyed. Unreachable today. */
	DESTROYED,
	/** Not selectable: temporarily unavailable for a reason that is neither recovery nor loss. Unreachable today. */
	TEMPORARY;

	/**
	 * The state of one roster slot, from the two facts the server owns: whether a live pack of that
	 * type is out, and how much of its cooldown is left.
	 *
	 * <p>Cooldown outranks the summoned marker. The two cannot both be true for one type through the
	 * production paths — a pack's cooldown is charged when the pack is swept, and nothing summons a
	 * cooling type — but a snapshot must still be deterministic if they ever are.
	 */
	public static MegumiShikigamiSlotState derive(boolean summoned, int remainingTicks) {
		if (remainingTicks > 0) {
			return COOLDOWN;
		}
		return summoned ? SUMMONED : READY;
	}
}
