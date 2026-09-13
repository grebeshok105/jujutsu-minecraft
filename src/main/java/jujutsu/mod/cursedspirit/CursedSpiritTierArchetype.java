package jujutsu.mod.cursedspirit;

/**
 * The tier's archetype position inside a grade band: where in
 * {@code [min, max]} this morphology sits, per stat (0 = band bottom,
 * 1 = band top).
 *
 * <p>The tier no longer owns stats — it only nudges the grade-rolled value
 * within the band, and the final clamp keeps the nudge from crossing a band
 * edge. Speed order across grades therefore always follows the grade
 * (0.22–0.25 / 0.26–0.29 / 0.30–0.33); the old "lesser is fastest" identity
 * lives on in morphology and combat pattern, not in numbers (D2).
 *
 * <p><b>BALANCE:</b> the per-tier ranks below tune after in-game runs.
 */
public record CursedSpiritTierArchetype(double hpRank, double damageRank, double speedRank) {
	public CursedSpiritTierArchetype {
		if (!isRank(hpRank) || !isRank(damageRank) || !isRank(speedRank)) {
			throw new IllegalArgumentException(
					"archetype ranks must sit in [0, 1], got [" + hpRank + ", " + damageRank + ", " + speedRank + "]");
		}
	}

	private static boolean isRank(double rank) {
		return rank >= 0.0 && rank <= 1.0;
	}

	/** BALANCE: archetype ranks per tier (hp / damage / speed). */
	public static CursedSpiritTierArchetype of(CursedSpiritTier tier) {
		return switch (tier) {
			case LESSER -> new CursedSpiritTierArchetype(0.35, 0.40, 0.70);
			case COMMON -> new CursedSpiritTierArchetype(0.50, 0.50, 0.45);
			case GREATER -> new CursedSpiritTierArchetype(0.75, 0.65, 0.25);
		};
	}
}
