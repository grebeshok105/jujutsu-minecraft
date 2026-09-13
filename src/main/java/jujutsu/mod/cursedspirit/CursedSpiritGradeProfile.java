package jujutsu.mod.cursedspirit;

/**
 * Every grade balance number. The single editable grade profile: rolls read
 * these rows, never literals (pattern-mutation proof lives in
 * {@code CursedSpiritRollPolicyTest} — a mutated band moves the roll output).
 *
 * <p><b>BALANCE:</b> all bands, weights and the tier-rank spread below tune
 * after in-game runs; the spec fixes only the shape (60/30/10, disjoint
 * bands, centre concentration), not the numbers.
 */
public final class CursedSpiritGradeProfile {
	private CursedSpiritGradeProfile() {}

	/** BALANCE: v1 spawn weights, weakest first (60/30/10, grades 2–1 closed). */
	public static int v1Weight(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> 60;
			case GRADE_4 -> 30;
			case GRADE_3 -> 10;
			case GRADE_2, GRADE_1 -> 0;
		};
	}

	/** BALANCE: absolute HP bands — disjoint across grades by construction. */
	public static CursedSpiritGradeBand health(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> new CursedSpiritGradeBand(12.0, 16.0, 20.0);
			case GRADE_4 -> new CursedSpiritGradeBand(26.0, 32.0, 40.0);
			case GRADE_3 -> new CursedSpiritGradeBand(52.0, 64.0, 78.0);
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has model space but no v1 content");
		};
	}

	/** BALANCE: absolute damage bands — disjoint across grades by construction. */
	public static CursedSpiritGradeBand damage(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> new CursedSpiritGradeBand(3.0, 4.0, 5.0);
			case GRADE_4 -> new CursedSpiritGradeBand(6.0, 7.5, 9.0);
			case GRADE_3 -> new CursedSpiritGradeBand(11.0, 13.0, 16.0);
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has model space but no v1 content");
		};
	}

	/** BALANCE: absolute speed bands — disjoint across grades by construction. */
	public static CursedSpiritGradeBand speed(CursedSpiritGrade grade) {
		return switch (grade) {
			case GRADE_5 -> new CursedSpiritGradeBand(0.22, 0.235, 0.25);
			case GRADE_4 -> new CursedSpiritGradeBand(0.26, 0.275, 0.29);
			case GRADE_3 -> new CursedSpiritGradeBand(0.30, 0.315, 0.33);
			case GRADE_2, GRADE_1 -> throw new IllegalStateException(
					grade + " has model space but no v1 content");
		};
	}

	/**
	 * How many uniform draws one stat roll averages. 3 gives the spec's
	 * centre-concentrated bell (outer quartiles ≈ 13%, not uniform's 50%);
	 * the shape constant lives here so a test-side band mutation moves the
	 * derivation (pattern-mutation, not R20).
	 */
	public static int statRolls() {
		return 3;
	}

	/**
	 * BALANCE: how far the tier archetype rank shifts a rolled stat, as a
	 * fraction of the band width (rank 0 → down by half of this, rank 1 →
	 * up). The final clamp keeps the shift inside the band, so the grade
	 * invariant survives any rank.
	 */
	public static double tierRankSpread() {
		return 0.5;
	}

	/**
	 * Registration placeholder: attribute defaults for the pre-roll window
	 * (type registration needs numbers before any individual exists).
	 * {@code applyStats} overwrites these on both server paths
	 * ({@code finalizeSpawn}, end of {@code readAdditionalSaveData}), so no
	 * live body ever fights on them.
	 */
	public static CursedSpiritGradeStats registrationDefaults() {
		CursedSpiritGradeBand hp = health(CursedSpiritGrade.GRADE_5);
		CursedSpiritGradeBand dmg = damage(CursedSpiritGrade.GRADE_5);
		CursedSpiritGradeBand spd = speed(CursedSpiritGrade.GRADE_5);
		return new CursedSpiritGradeStats(hp.norm(), dmg.norm(), spd.norm());
	}
}
