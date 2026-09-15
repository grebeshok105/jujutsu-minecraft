package jujutsu.mod.cursedspirit;

import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;

/**
 * Graded daytime-shelter policy for the cursed spirits (#82, Block 4, Step 3; closes review #21).
 *
 * <p>A single boolean ({@code canSeeSky}) cannot express "tree crack vs low roof", so shelter is
 * a 0..1 score: open sky scores 0, shade/canopy/partial roof lands around 0.4-0.6, a low ceiling
 * ({@link #LOW_CEILING_GAP} air blocks or fewer above the head) scores 1.0. Sky light is one
 * factor, never the only one.
 *
 * <p>Shape: pure core ({@link #score}, {@link #wants}, {@link #pickTarget}) for JUnit plus a thin
 * world adapter ({@link #shelterScoreAt}) for the goal and the GameTests. Numbers live here, next
 * to the derivation — never as literals in the goal.
 */
public final class CursedSpiritShelterPolicy {
	private CursedSpiritShelterPolicy() {}

	/** Enter threshold: the goal engages while the current score is below this. */
	public static final double WANT_BELOW = 0.5;
	/** Settled threshold: the goal stops once the current score reaches this. */
	public static final double SETTLED_AT = 0.9;
	/** A ceiling this many air blocks above the head (or fewer) is full shelter. */
	public static final int LOW_CEILING_GAP = 3;
	/** Up-scan budget for the ceiling search; uncapped sky simply exceeds the low-ceiling band. */
	public static final int SCAN_UP_BLOCKS = 8;
	/** Goal re-scans at most this often; the cached target carries the ticks between scans. */
	public static final int SCAN_PERIOD_TICKS = 40;
	/** Horizontal half-extent of the candidate ring around the body. */
	public static final int SCAN_RADIUS_BLOCKS = 8;
	/** Navigation speed modifier for the shelter walk. BALANCE, not design truth. */
	public static final double SEEK_SPEED = 1.0;
	/** Standable-candidate budget per scan (nearest-first), so one scan cannot stall a tick. */
	public static final int MAX_CANDIDATES = 48;
	/** One run gives up after this many failed navigations toward the picked cell. */
	public static final int MAX_NAV_FAILURES = 5;
	/** One run gives up after this many ticks without reaching the picked cell. */
	public static final int NAV_TIMEOUT_TICKS = 300;
	/** Horizontal distance to the cell centre that counts as arrived. BALANCE. */
	public static final double ARRIVE_RADIUS_BLOCKS = 1.5;
	/** Vertical slack for the arrival check (one block up/down still counts). */
	public static final double ARRIVE_RADIUS_Y = 2.0;

	private static final double PARTIAL_BASE = 0.4;
	private static final double PARTIAL_RANGE = 0.2;
	private static final int MAX_SKY_LIGHT = 15;

	/**
	 * Pure score core: open sky is 0; a low ceiling is 1.0; anything roofed-but-roomy grades with
	 * the remaining sky light into [0.4, 0.6]. {@code ceilingGap} is air blocks above the head
	 * before the first opaque block ({@link #SCAN_UP_BLOCKS} when the sky is open-ended).
	 */
	public static double score(boolean openSky, int skyLight, int ceilingGap) {
		if (openSky) {
			return 0.0;
		}
		if (ceilingGap <= LOW_CEILING_GAP) {
			return 1.0;
		}
		int clampedSky = Math.min(Math.max(skyLight, 0), MAX_SKY_LIGHT);
		double shade = 1.0 - clampedSky / (double) MAX_SKY_LIGHT;
		return PARTIAL_BASE + PARTIAL_RANGE * shade;
	}

	/**
	 * World adapter: openness from {@code canSeeSky} above the head, shade from the sky-light
	 * layer at the feet, headroom from an upward scan for the first {@code isSolidRender} block.
	 * Javap-verified on 1.21.8: {@code canSeeSky}/{@code getBrightness} are
	 * {@code BlockAndTintGetter} defaults reachable from any {@code LevelReader}, and
	 * {@code isSolidRender()} is a no-arg {@code BlockStateBase} query.
	 */
	public static double shelterScoreAt(LevelReader level, BlockPos feetPos) {
		BlockPos head = feetPos.above(2);
		boolean openSky = level.canSeeSky(head);
		int skyLight = level.getBrightness(LightLayer.SKY, feetPos);
		int gap = 0;
		BlockPos cursor = head;
		while (gap < SCAN_UP_BLOCKS && !level.getBlockState(cursor).isSolidRender()) {
			cursor = cursor.above();
			gap++;
		}
		return score(openSky, skyLight, gap);
	}

	/**
	 * Soft preference, not a hard rule: by day, out of combat, and only while the current spot
	 * scores below {@link #WANT_BELOW}. Night or combat always returns false.
	 */
	public static boolean wants(boolean isDay, boolean inCombat, double currentScore) {
		return isDay && !inCombat && currentScore < WANT_BELOW;
	}

	/**
	 * Best-score candidate wins; ties break randomly so sibling bodies do not stack on one cell.
	 * Null or empty input returns null (no candidates, no target) instead of falling over.
	 */
	public static BlockPos pickTarget(List<BlockPos> candidates, ToDoubleFunction<BlockPos> scoreFn,
			RandomSource rng) {
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		BlockPos best = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		int bestCount = 0;
		for (BlockPos candidate : candidates) {
			double value = scoreFn.applyAsDouble(candidate);
			if (value > bestScore) {
				bestScore = value;
				best = candidate;
				bestCount = 1;
			} else if (value == bestScore) {
				bestCount++;
				if (rng.nextInt(bestCount) == 0) {
					best = candidate;
				}
			}
		}
		return best;
	}
}
