package jujutsu.mod.cursedspirit;

/**
 * Every non-grade balance number for the cursed-spirit tiers. The single
 * editable profile (R20): brains, goals and spawn rows read these rows,
 * never literals.
 *
 * <p>Power stats (HP/damage/speed) are NOT here — they moved to the grade
 * axis ({@link CursedSpiritGradeProfile} + per-individual
 * {@link CursedSpiritGradeStats}, D2). What stays per tier is morphology and
 * combat pattern: follow range, knockback resistance, stagger recovery,
 * windup/cooldown/reach/AoE, XP and the natural-spawn rows.
 */
public final class CursedSpiritProfile {
	private CursedSpiritProfile() {}

	/** Local crowd cap: at most this many spirits within {@link #CROWD_RADIUS} of a spawn spot. */
	public static final int MAX_SPIRITS_NEARBY = 10;
	/** Radius in blocks for the local crowd cap. */
	public static final double CROWD_RADIUS = 48.0;

	public static CursedSpiritTierStats of(CursedSpiritTier tier) {
		return switch (tier) {
			case LESSER -> new CursedSpiritTierStats(
					16.0, 0.0, 1.0,
					5, 20, 2.0, 0.4, 0.0,
					0.0, 0.0, 0.0, 5, 65, 2, 4);
			case COMMON -> new CursedSpiritTierStats(
					24.0, 0.0, 1.0,
					9, 30, 2.5, 0.9, 0.35,
					0.0, 0.0, 0.0, 15, 20, 1, 2);
			case GREATER -> new CursedSpiritTierStats(
					32.0, 0.85, 0.4,
					14, 60, 3.0, 1.0, 0.0,
					3.5, 0.6, 1.2, 40, 5, 1, 1);
		};
	}
}
