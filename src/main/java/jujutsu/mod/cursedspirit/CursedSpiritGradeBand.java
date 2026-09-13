package jujutsu.mod.cursedspirit;

/**
 * One absolute stat band of one grade: {@code [min, max]} with the
 * distribution centre at {@code norm}.
 *
 * <p>Bands are the grade invariant made structural: the profile table keeps
 * every grade's band disjoint from its neighbours
 * ({@code max(5) < min(4) < min(3)} per stat), the roll clamps into the band,
 * and the table-level test {@code bandsAreDisjointAcrossAllStats} pins the
 * table itself.
 */
public record CursedSpiritGradeBand(double min, double norm, double max) {
	public CursedSpiritGradeBand {
		if (!(min < max) || norm < min || norm > max) {
			throw new IllegalArgumentException(
					"band must satisfy min < max with norm inside, got [" + min + ", " + norm + ", " + max + "]");
		}
	}

	/** Band width ({@code max - min}), the roll's full swing. */
	public double width() {
		return max - min;
	}

	/** Clamps a final value into the band. The grade invariant's second half. */
	public double clamp(double value) {
		return Math.min(max, Math.max(min, value));
	}

	/** Whether a final value sits inside the band (edges count). */
	public boolean contains(double value) {
		return value >= min && value <= max;
	}
}
