package jujutsu.mod.cursedspirit.ability;

import jujutsu.mod.cursedspirit.CursedSpiritGrade;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * Data tables for the v1 ability pool (Block 3, #86).
 *
 * <p>All numbers are BALANCE placeholders, tunable after in-game runs without spec changes.
 * Grade enters only as finished per-grade rows selected by {@link #of}: consumers never scale
 * by grade level, and strength comparisons use {@code grade.powerRank()}, never the raw 5..1.
 */
public final class CursedSpiritAbilityProfile {
	/** Fear plays a short cast clip while the debuff outlives it; the clip never blocks melee. */
	public static final int FEAR_CAST_TICKS = 20;
	/** HoT pulse period for regen. */
	public static final int REGEN_PULSE_PERIOD_TICKS = 20;
	/** Latched berserk needs no re-trigger window. */
	public static final int PASSIVE_COOLDOWN_TICKS = 0;
	/** HP fraction at or below which damage latches berserk. BALANCE. */
	private static final double BERSERK_THRESHOLD = 0.35;
	/** HP fraction at or below which the brain considers regen. BALANCE. */
	private static final double REGEN_TRIGGER_FRACTION = 0.6;
	/** Acid plays a short spit clip while glob and zones do the work; the clip never lingers. */
	public static final int ACID_CAST_TICKS = 10;
	/** In-combat decide ranges (blocks). BALANCE: all of them. */
	public static final double DASH_MIN_RANGE = 4.0;
	public static final double DASH_MAX_RANGE = 16.0;
	public static final double SLAM_RANGE = 5.0;
	public static final double ACID_MIN_RANGE = 4.0;
	public static final double ACID_MAX_RANGE = 18.0;
	public static final double RUNNER_MIN_RANGE = 3.0;
	public static final double RUNNER_MAX_RANGE = 12.0;
	private static final int VARIANT_BIAS = 2;
	/** "Nasty" abilities (fear/berserk/runner/acid) weigh 8/10/12 across grades 5/4/3. BALANCE. */
	private static final int NASTY_WEIGHT_GRADE_5 = 8;
	private static final int NASTY_WEIGHT_GRADE_4 = 10;
	private static final int NASTY_WEIGHT_GRADE_3 = 12;
	/** Everything else weighs a flat 10 at every grade; passives are never down-weighted. */
	private static final int FLAT_WEIGHT = 10;

	private CursedSpiritAbilityProfile() {
	}

	/**
	 * Finished parameters for one ability at one grade. Grade rows are absolute; adding a grade
	 * is a table edit, never arithmetic.
	 */
	public static CursedSpiritAbilityParams of(CursedSpiritAbilityId id, CursedSpiritGrade grade) {
		return switch (id) {
			case DASH -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(4.0, 1.2, 14, 0.9, 0.5, 200, FLAT_WEIGHT);
				case GRADE_4 -> new CursedSpiritAbilityParams(6.0, 1.2, 14, 1.0, 0.5, 180, FLAT_WEIGHT);
				case GRADE_3 -> new CursedSpiritAbilityParams(9.0, 1.3, 16, 1.1, 0.6, 160, FLAT_WEIGHT);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case GROUND_SLAM -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(5.0, 3.0, 40, 0.7, 0.5, 260, FLAT_WEIGHT);
				case GRADE_4 -> new CursedSpiritAbilityParams(8.0, 3.5, 40, 0.75, 0.5, 240, FLAT_WEIGHT);
				case GRADE_3 -> new CursedSpiritAbilityParams(12.0, 4.0, 40, 0.8, 0.6, 220, FLAT_WEIGHT);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case ACID_SPIT -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(3.0, 2.0, 100, 0.9, 2.0, 180,
						NASTY_WEIGHT_GRADE_5);
				case GRADE_4 -> new CursedSpiritAbilityParams(4.0, 2.5, 120, 1.0, 3.0, 160,
						NASTY_WEIGHT_GRADE_4);
				case GRADE_3 -> new CursedSpiritAbilityParams(6.0, 3.0, 140, 1.1, 4.0, 180,
						NASTY_WEIGHT_GRADE_3);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case GRAB_RUNNER -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(0.0, 0.0, 80, 1.3, 0.0, 320,
						NASTY_WEIGHT_GRADE_5);
				case GRADE_4 -> new CursedSpiritAbilityParams(0.0, 0.0, 80, 1.4, 0.0, 300,
						NASTY_WEIGHT_GRADE_4);
				case GRADE_3 -> new CursedSpiritAbilityParams(0.0, 0.0, 80, 1.5, 0.0, 280,
						NASTY_WEIGHT_GRADE_3);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case FEAR -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(0.0, 8.0, 100, 0.0, 0.0, 320,
						NASTY_WEIGHT_GRADE_5);
				case GRADE_4 -> new CursedSpiritAbilityParams(0.0, 10.0, 140, 0.0, 0.0, 300,
						NASTY_WEIGHT_GRADE_4);
				case GRADE_3 -> new CursedSpiritAbilityParams(0.0, 12.0, 180, 0.0, 0.0, 280,
						NASTY_WEIGHT_GRADE_3);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case REGEN -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(0.0, 0.0, 120, 0.0, 1.0, 420, FLAT_WEIGHT);
				case GRADE_4 -> new CursedSpiritAbilityParams(0.0, 0.0, 140, 0.0, 1.5, 400, FLAT_WEIGHT);
				case GRADE_3 -> new CursedSpiritAbilityParams(0.0, 0.0, 160, 0.0, 2.0, 380, FLAT_WEIGHT);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case ARMOR -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.0, 2.0,
						PASSIVE_COOLDOWN_TICKS, FLAT_WEIGHT);
				case GRADE_4 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.0, 3.0,
						PASSIVE_COOLDOWN_TICKS, FLAT_WEIGHT);
				case GRADE_3 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.0, 4.0,
						PASSIVE_COOLDOWN_TICKS, FLAT_WEIGHT);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
			case BERSERK -> switch (grade) {
				case GRADE_5 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.10, 0.3,
						PASSIVE_COOLDOWN_TICKS, NASTY_WEIGHT_GRADE_5);
				case GRADE_4 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.12, 0.4,
						PASSIVE_COOLDOWN_TICKS, NASTY_WEIGHT_GRADE_4);
				case GRADE_3 -> new CursedSpiritAbilityParams(0.0, 0.0, 0, 0.15, 0.5,
						PASSIVE_COOLDOWN_TICKS, NASTY_WEIGHT_GRADE_3);
				case GRADE_2, GRADE_1 -> throw new IllegalStateException("No v1 content for " + grade);
			};
		};
	}

	/** v1: every ability is available to every spawnable grade (rank gate 1 = weakest). */
	public static int requiredPowerRank(CursedSpiritAbilityId id) {
		return switch (id) {
			case DASH, GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, FEAR, REGEN, ARMOR, BERSERK -> 1;
		};
	}

	/**
	 * Variant signature bump (C3): +2 to each variant's two characteristic ids, 0 elsewhere.
	 * v1 has no variant-unique pools — the variant only bends the weights.
	 */
	public static int variantBias(CursedSpiritAbilityId id, CursedSpiritVariant variant) {
		return switch (variant) {
			case PROWLER -> switch (id) {
				case DASH, GROUND_SLAM -> VARIANT_BIAS;
				case ACID_SPIT, GRAB_RUNNER, FEAR, REGEN, ARMOR, BERSERK -> 0;
			};
			case FLOATING_CURSE -> switch (id) {
				case FEAR, DASH -> VARIANT_BIAS;
				case GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, REGEN, ARMOR, BERSERK -> 0;
			};
			case GULBER -> switch (id) {
				case ACID_SPIT, BERSERK -> VARIANT_BIAS;
				case DASH, GROUND_SLAM, GRAB_RUNNER, FEAR, REGEN, ARMOR -> 0;
			};
			case KELVIN -> switch (id) {
				case GROUND_SLAM, ARMOR -> VARIANT_BIAS;
				case DASH, ACID_SPIT, GRAB_RUNNER, FEAR, REGEN, BERSERK -> 0;
			};
			case BUTCHER -> switch (id) {
				case BERSERK, DASH -> VARIANT_BIAS;
				case GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, FEAR, REGEN, ARMOR -> 0;
			};
			case GUZZLER -> switch (id) {
				case GRAB_RUNNER, REGEN -> VARIANT_BIAS;
				case DASH, GROUND_SLAM, ACID_SPIT, FEAR, ARMOR, BERSERK -> 0;
			};
			case BLUD -> switch (id) {
				case REGEN, FEAR -> VARIANT_BIAS;
				case DASH, GROUND_SLAM, ACID_SPIT, GRAB_RUNNER, ARMOR, BERSERK -> 0;
			};
			case WALKING_BED -> switch (id) {
				case ARMOR, GROUND_SLAM -> VARIANT_BIAS;
				case DASH, ACID_SPIT, GRAB_RUNNER, FEAR, REGEN, BERSERK -> 0;
			};
			case WISTIVER -> switch (id) {
				case FEAR, ACID_SPIT -> VARIANT_BIAS;
				case DASH, GROUND_SLAM, GRAB_RUNNER, REGEN, ARMOR, BERSERK -> 0;
			};
		};
	}

	public static double berserkThreshold() {
		return BERSERK_THRESHOLD;
	}

	public static double regenTriggerFraction() {
		return REGEN_TRIGGER_FRACTION;
	}
}
