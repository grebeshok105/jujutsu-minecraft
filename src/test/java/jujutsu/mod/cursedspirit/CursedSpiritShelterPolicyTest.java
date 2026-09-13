package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/**
 * Graded-shelter contract (Block 4, Step 3; closes review #21): the score graduates from open
 * sky (0) through shade (0.4-0.6) to a low ceiling (1.0), {@code wants} is day-only, out of
 * combat and below-threshold, and {@code pickTarget} takes the best score with random tie-breaks.
 * Only the pure core is exercised here (no world, no bootstrap): the world adapter
 * ({@code shelterScoreAt}) is observed by the shelter GameTests instead.
 *
 * <p>Red-proof: collapsing the score to {@code !canSeeSky} (boolean shelter) reddens
 * {@code shadeGradesStrictlyBetweenOpenAndLowRoof}, because the tree crack and the low roof
 * would then score identically.
 */
final class CursedSpiritShelterPolicyTest {
	@Test
	void openSkyScoresZero() {
		assertEquals(0.0, CursedSpiritShelterPolicy.score(true, 15, 8), 1e-12, "open noon sky");
		assertEquals(0.0, CursedSpiritShelterPolicy.score(true, 0, 0), 1e-12,
				"open sky ignores light and ceiling");
	}

	@Test
	void lowCeilingScoresOne() {
		assertEquals(1.0, CursedSpiritShelterPolicy.score(false, 0, 1), 1e-12, "one block above head");
		assertEquals(1.0, CursedSpiritShelterPolicy.score(false, 15, 3), 1e-12,
				"three blocks still full shelter despite bright sky");
	}

	@Test
	void shadeGradesStrictlyBetweenOpenAndLowRoof() {
		double treeCrack = CursedSpiritShelterPolicy.score(false, 10, 8);
		double deepShade = CursedSpiritShelterPolicy.score(false, 2, 8);
		assertTrue(treeCrack >= 0.4 && treeCrack <= 0.6, "tree crack in the partial band: " + treeCrack);
		assertTrue(deepShade >= 0.4 && deepShade <= 0.6, "deep shade in the partial band: " + deepShade);
		assertTrue(treeCrack < deepShade, "darker shade outscores a bright crack");
		assertTrue(treeCrack < CursedSpiritShelterPolicy.score(false, 10, 2),
				"tree crack below a low roof at the same light");
		assertTrue(0.0 < treeCrack && treeCrack < 1.0, "strictly between open and roofed");
	}

	@Test
	void partialBandClampsWildSkyLight() {
		assertEquals(CursedSpiritShelterPolicy.score(false, 0, 8),
				CursedSpiritShelterPolicy.score(false, -50, 8), 1e-12, "negative sky clamps to 0");
		assertEquals(CursedSpiritShelterPolicy.score(false, 15, 8),
				CursedSpiritShelterPolicy.score(false, 99, 8), 1e-12, "overbright sky clamps to 15");
	}

	@Test
	void wantsIsDayOnlyOutOfCombatBelowThreshold() {
		assertTrue(CursedSpiritShelterPolicy.wants(true, false, 0.0), "day, calm, open wants shelter");
		assertTrue(CursedSpiritShelterPolicy.wants(true, false, 0.49), "just below threshold wants");
		assertFalse(CursedSpiritShelterPolicy.wants(true, false, 0.5), "at threshold stays");
		assertFalse(CursedSpiritShelterPolicy.wants(true, false, 1.0), "settled stays");
		assertFalse(CursedSpiritShelterPolicy.wants(true, true, 0.0), "combat never wants");
		assertFalse(CursedSpiritShelterPolicy.wants(false, false, 0.0), "night never wants");
		assertFalse(CursedSpiritShelterPolicy.wants(false, true, 0.0), "night combat never wants");
	}

	@Test
	void pickTargetTakesTheUniqueBest() {
		List<BlockPos> candidates = List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 0),
				new BlockPos(8, 1, 0));
		Map<BlockPos, Double> scores = Map.of(new BlockPos(0, 1, 0), 0.0, new BlockPos(4, 1, 0), 1.0,
				new BlockPos(8, 1, 0), 0.5);
		BlockPos best = CursedSpiritShelterPolicy.pickTarget(candidates, scores::get,
				RandomSource.create(7L));
		assertEquals(new BlockPos(4, 1, 0), best, "unique best wins regardless of seed");
	}

	@Test
	void pickTargetBreaksTiesRandomlyButStaysOnTop() {
		List<BlockPos> candidates = List.of(new BlockPos(0, 1, 0), new BlockPos(4, 1, 0),
				new BlockPos(8, 1, 0));
		Map<BlockPos, Double> scores = Map.of(new BlockPos(0, 1, 0), 1.0, new BlockPos(4, 1, 0), 1.0,
				new BlockPos(8, 1, 0), 1.0);
		boolean sawFirst = false;
		boolean sawOther = false;
		for (long seed = 0; seed < 20; seed++) {
			BlockPos pick = CursedSpiritShelterPolicy.pickTarget(candidates, scores::get,
					RandomSource.create(seed));
			if (pick.equals(new BlockPos(0, 1, 0))) {
				sawFirst = true;
			} else {
				sawOther = true;
			}
		}
		assertTrue(sawFirst && sawOther, "ties spread across the top set over 20 seeds");
	}

	@Test
	void pickTargetToleratesEmptyAndNull() {
		assertNull(CursedSpiritShelterPolicy.pickTarget(List.of(), pos -> 1.0, RandomSource.create(1L)),
				"empty list has no target");
		assertNull(CursedSpiritShelterPolicy.pickTarget(null, pos -> 1.0, RandomSource.create(1L)),
				"null list has no target");
	}

	@Test
	void pickTargetIgnoresOrder() {
		Map<BlockPos, Double> scores = new HashMap<>();
		List<BlockPos> forward = new ArrayList<>();
		for (int x = 0; x < 10; x++) {
			BlockPos spot = new BlockPos(x, 1, 0);
			forward.add(spot);
			scores.put(spot, x == 7 ? 0.95 : 0.1 * (x % 3));
		}
		List<BlockPos> backward = new ArrayList<>(forward);
		java.util.Collections.reverse(backward);
		assertEquals(new BlockPos(7, 1, 0),
				CursedSpiritShelterPolicy.pickTarget(forward, scores::get, RandomSource.create(3L)),
				"best wins in forward order");
		assertEquals(new BlockPos(7, 1, 0),
				CursedSpiritShelterPolicy.pickTarget(backward, scores::get, RandomSource.create(3L)),
				"best wins in reverse order");
	}
}
