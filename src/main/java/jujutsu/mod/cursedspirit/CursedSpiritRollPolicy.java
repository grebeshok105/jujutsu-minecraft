package jujutsu.mod.cursedspirit;

import java.util.Optional;
import net.minecraft.util.RandomSource;

/**
 * Pure newborn math for the grade axis (issue #81). No level, no entity, no
 * NBT — every number comes from {@link CursedSpiritGradeProfile} bands or the
 * passed archetype, so tests mutate a band and watch the derivation move.
 *
 * <p><b>Roll order contract (frozen, C2):</b> one {@code rollSeed} per body,
 * one {@code RandomSource.create(rollSeed)} stream, consumed strictly as
 * {@code grade → variant → stats → abilities}. {@link #rollNewborn} performs
 * the first three stages in one call so the order cannot drift; the ability
 * stage (block 3) continues the same stream by replaying from the seed and
 * then drawing — never by reordering.
 */
public final class CursedSpiritRollPolicy {
	private CursedSpiritRollPolicy() {}

	/** One newborn roll: grade, then variant, then stats — the frozen order. */
	public record Newborn(CursedSpiritGrade grade, CursedSpiritVariant variant, CursedSpiritGradeStats stats) {}

	/**
	 * Rolls grade → variant → stats from one stream, in contract order.
	 * The entity calls this once in {@code finalizeSpawn} and once for the
	 * NBT fallback; block 3 replays it from the seed to reach the ability
	 * stage on the same stream.
	 */
	public static Newborn rollNewborn(RandomSource random, CursedSpiritTier tier) {
		CursedSpiritGrade grade = rollGrade(random);
		CursedSpiritVariant variant = CursedSpiritEntity.rollVariant(tier, random);
		return new Newborn(grade, variant, rollStats(random, grade, CursedSpiritTierArchetype.of(tier)));
	}

	/** Pure weighted-selection core: {@code roll} in {@code [0, totalWeight)}. */
	public static int selectGradeIndex(int[] weights, int roll) {
		int cursor = roll;
		for (int i = 0; i < weights.length; i++) {
			cursor -= weights[i];
			if (cursor < 0) {
				return i;
			}
		}
		return weights.length - 1;
	}

	/** Weighted v1 grade roll over {@link CursedSpiritGrade#SPAWNABLE_V1}. */
	public static CursedSpiritGrade rollGrade(RandomSource random) {
		int total = 0;
		int[] weights = new int[CursedSpiritGrade.SPAWNABLE_V1.size()];
		for (int i = 0; i < weights.length; i++) {
			weights[i] = CursedSpiritGradeProfile.v1Weight(CursedSpiritGrade.SPAWNABLE_V1.get(i));
			total += weights[i];
		}
		return CursedSpiritGrade.SPAWNABLE_V1.get(selectGradeIndex(weights, random.nextInt(total)));
	}

	/**
	 * One centre-concentrated stat roll: the mean of {@code rolls} uniforms
	 * mapped so the distribution peaks at the band norm, then clamped into
	 * the band. Extremes stay possible, never common (spec: not
	 * {@code uniform(min, max)}).
	 */
	public static double rollStat(RandomSource random, CursedSpiritGradeBand band, int rolls) {
		if (rolls < 1) {
			throw new IllegalArgumentException("stat rolls must be >= 1, got " + rolls);
		}
		double sum = 0.0;
		for (int i = 0; i < rolls; i++) {
			sum += random.nextDouble();
		}
		double centred = sum / rolls;
		return band.clamp(band.norm() + (centred - 0.5) * band.width());
	}

	/**
	 * Three independent stat rolls plus the tier archetype nudge, each final
	 * clamped into its grade band. Independence is literal: three separate
	 * {@link #rollStat} draws, so HP says nothing about speed.
	 */
	public static CursedSpiritGradeStats rollStats(RandomSource random, CursedSpiritGrade grade,
			CursedSpiritTierArchetype archetype) {
		return new CursedSpiritGradeStats(
				blendStat(random, CursedSpiritGradeProfile.health(grade), archetype.hpRank()),
				blendStat(random, CursedSpiritGradeProfile.damage(grade), archetype.damageRank()),
				blendStat(random, CursedSpiritGradeProfile.speed(grade), archetype.speedRank()));
	}

	private static double blendStat(RandomSource random, CursedSpiritGradeBand band, double rank) {
		int rolls = CursedSpiritGradeProfile.statRolls();
		double rolled = rollStat(random, band, rolls);
		double nudged = rolled + (rank - 0.5) * band.width() * CursedSpiritGradeProfile.tierRankSpread();
		return band.clamp(nudged);
	}

	/**
	 * Maps a loaded grade level to the effective grade. Every 1..5 level is
	 * a valid model value (grades 2–1 keep their enum slot); garbage is
	 * empty. Whether the grade may live in v1 is the caller's gate
	 * ({@link CursedSpiritGrade#SPAWNABLE_V1}), not this function's.
	 */
	public static Optional<CursedSpiritGrade> resolveLoaded(int level) {
		return CursedSpiritGrade.byLevel(level);
	}
}
