package jujutsu.mod.character.megumi;

/**
 * Tuning for Megumi's partial manifestations (issue #108) and the one table that says which
 * selection brings which partial out.
 *
 * <p>The table is the requirement: only Nue and Toad manifest partially (R37), and the check is
 * written as a projection of this enum rather than as a chain of {@code if (selection == ...)} in the
 * runtime — a shikigami appended without a row here answers "no partial" through
 * {@link PartialKind#forSelection(MegumiShikigami)} and fails the JUnit tripwire instead of silently
 * gaining or losing a key.
 *
 * <p>Both partials are free by design (R34): nothing in this file prices a cooldown, because none
 * exists.
 */
public final class MegumiPartialProfile {
	private MegumiPartialProfile() {}

	/**
	 * How far the tongue reaches for a surface. Ten blocks is the requirement (R29) and it is spent
	 * entirely on the aim ray: the anchor is the point where that ray meets an opaque collider, so a
	 * hit at exactly ten blocks hooks and anything past it resolves as no anchor at all.
	 */
	public static final double TONGUE_RANGE = 10.0;

	/**
	 * How close a re-clip has to land to the anchor to count as "the line to it is still clear".
	 *
	 * <p>The anchor is a point on a block face, so the per-tick line-of-sight re-clip ends on that
	 * surface and hits it: within this tolerance the clip hit the anchored face itself, and anything
	 * nearer is a body that moved into the line — the break condition (R31). A quarter of a block is
	 * a quarter of the smallest collider the game has, so it cannot be satisfied by an occluder.
	 */
	public static final double TONGUE_LINE_TOLERANCE = 0.25;

	/** The two partial manifestations. Which shikigami each belongs to is the exclusion key (§19). */
	public enum PartialKind {
		/** Nue's wings: fall-flying while the marker effect is carried, folded by landing (D2). */
		WINGS(MegumiShikigami.NUE),
		/** Toad's tongue: a hold-to-anchor grapple, attached to a surface within {@link #TONGUE_RANGE}. */
		TONGUE(MegumiShikigami.TOAD);

		private final MegumiShikigami type;

		PartialKind(MegumiShikigami type) {
			this.type = type;
		}

		/** The shikigami this partial manifests. */
		public MegumiShikigami type() {
			return type;
		}

		/** The partial the given selection brings out, or null when that shikigami has none (R21/R37). */
		public static PartialKind forSelection(MegumiShikigami selected) {
			for (PartialKind kind : values()) {
				if (kind.type == selected) {
					return kind;
				}
			}
			return null;
		}
	}
}
