package jujutsu.mod.cursedspirit;

/**
 * Every balance number for the cursed-spirit tiers. The single editable profile (R20): brains,
 * goals and spawn rows read these rows, never literals.
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
					14.0, 3.0, 0.30, 16.0, 0.0, 1.0,
					5, 20, 2.0, 0.4, 0.0,
					0.0, 0.0, 0.0, 5, 65, 2, 4);
			case COMMON -> new CursedSpiritTierStats(
					45.0, 5.0, 0.24, 24.0, 0.0, 1.0,
					9, 30, 2.5, 0.9, 0.35,
					0.0, 0.0, 0.0, 15, 20, 1, 2);
			case GREATER -> new CursedSpiritTierStats(
					150.0, 8.0, 0.17, 32.0, 0.85, 0.4,
					14, 60, 3.0, 1.0, 0.0,
					3.5, 0.6, 1.2, 40, 5, 1, 1);
		};
	}
}
