package jujutsu.mod.cursedincident.policy;

import net.minecraft.util.RandomSource;

/** Pure seal requirements, integrity bounds and rare catastrophic failure rolls. */
public final class SealPolicy {
	public static final int MIN_TIER = 1;
	public static final int MAX_TIER = 3;
	public static final int TIER_1_INTEGRITY = 100;
	public static final int TIER_2_INTEGRITY = 200;
	public static final int TIER_3_INTEGRITY = 400;
	private static final double MIN_FAILURE_CHANCE = 0.001;
	private static final double MAX_FAILURE_CHANCE = 0.050;

	private SealPolicy() {
	}

	/** Minimum talisman tier (1..3) that can seal an object of the requested grade. */
	public static int requiredTier(int grade) {
		return switch (TemplateRollPolicy.clampGrade(grade)) {
			case 1, 2 -> 3;
			case 3, 4 -> 2;
			default -> 1;
		};
	}

	public static int integrityMax(int tier) {
		return switch (Math.max(MIN_TIER, Math.min(MAX_TIER, tier))) {
			case 1 -> TIER_1_INTEGRITY;
			case 2 -> TIER_2_INTEGRITY;
			default -> TIER_3_INTEGRITY;
		};
	}

	/**
	 * Returns true on a rare seeded failure. Probability is non-zero at full integrity,
	 * bounded at five percent, and rises monotonically as integrity approaches zero.
	 */
	public static boolean maybeCatastrophicFail(RandomSource random, int integrity) {
		int clamped = Math.max(0, Math.min(TIER_3_INTEGRITY, integrity));
		double damageFraction = 1.0 - clamped / (double) TIER_3_INTEGRITY;
		double chance = MIN_FAILURE_CHANCE + damageFraction * (MAX_FAILURE_CHANCE - MIN_FAILURE_CHANCE);
		return random.nextDouble() < chance;
	}

	public static double catastrophicFailureChance(int integrity) {
		int clamped = Math.max(0, Math.min(TIER_3_INTEGRITY, integrity));
		double damageFraction = 1.0 - clamped / (double) TIER_3_INTEGRITY;
		return MIN_FAILURE_CHANCE + damageFraction * (MAX_FAILURE_CHANCE - MIN_FAILURE_CHANCE);
	}
}
