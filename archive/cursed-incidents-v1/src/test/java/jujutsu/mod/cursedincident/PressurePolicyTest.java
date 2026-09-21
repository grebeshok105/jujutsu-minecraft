package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.cursedincident.policy.PressurePolicy;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** C2 and pressure accumulation contracts. */
class PressurePolicyTest {
	@Test
	void accumulationIsOnePointPerDay() {
		assertEquals(0L, PressurePolicy.accumulate(23_999));
		assertEquals(1L, PressurePolicy.accumulate(24_000));
		assertEquals(3L, PressurePolicy.accumulate(72_000));
		assertEquals(0L, PressurePolicy.accumulate(-1));
	}

	@Test
	void pressureChanceStartsAtZero() {
		assertEquals(0.0, PressurePolicy.spawnChance(0.0, 0), 0.0);
	}

	@Test
	void chanceCapsBeforeDamping() {
		assertEquals(0.9, PressurePolicy.spawnChance(10_000, 0), 1.0e-12);
	}

	@Test
	void dampingMatchesPinnedCurve() {
		assertEquals(1.0, PressurePolicy.damping(0), 1.0e-12);
		assertEquals(0.5, PressurePolicy.damping(1), 1.0e-12);
		assertEquals(0.25, PressurePolicy.damping(3), 1.0e-12);
	}

	@Test
	void dampingHasFloorAndNeverRefusesByCount() {
		assertEquals(0.05, PressurePolicy.damping(100), 1.0e-12);
		assertTrue(PressurePolicy.spawnChance(240, 100) > 0.0);
		assertTrue(PressurePolicy.spawnChance(240, Integer.MAX_VALUE) > 0.0);
	}

	@Test
	void activeZonesOnlyReduceChance() {
		double noZones = PressurePolicy.spawnChance(120, 0);
		double twoZones = PressurePolicy.spawnChance(120, 2);
		assertTrue(noZones > twoZones);
	}

	@Test
	void seededDecisionIsReproducible() {
		boolean first = PressurePolicy.shouldSpawn(RandomSource.create(99L), 180, 1);
		boolean second = PressurePolicy.shouldSpawn(RandomSource.create(99L), 180, 1);
		assertEquals(first, second);
	}

	@Test
	void zeroChanceNeverSpawns() {
		assertFalse(PressurePolicy.shouldSpawn(RandomSource.create(1L), 0, 0));
	}
}
