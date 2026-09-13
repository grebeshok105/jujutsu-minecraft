package jujutsu.mod.cursedspirit.perception;

/**
 * What one vessel may do with curses (issue #80, #83-compatible).
 *
 * <p>Two separate fields, deliberately — never a single {@code isSorcerer}. Perceiving a curse
 * (seeing/hearing it) and interacting with it (targeting, damaging, colliding) are different
 * permissions: future non-standard vessels (the Maki/Toji shape) may hold one without the other,
 * and a third field ({@code tools}) will join this record without touching its readers.
 */
public record PerceptionFlags(boolean perceiveCurses, boolean interactWithCurses) {
	/** Ordinary non-sorcerer: no perception, no interaction. */
	public static final PerceptionFlags NONE = new PerceptionFlags(false, false);

	/** Full sorcerer: perceives curses and fights them. */
	public static final PerceptionFlags PERCEIVER = new PerceptionFlags(true, true);
}
