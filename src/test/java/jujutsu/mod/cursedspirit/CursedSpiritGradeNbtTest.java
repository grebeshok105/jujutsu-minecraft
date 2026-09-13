package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Grade NBT schema contract (Block 2, Step 5, pure half). Keys are frozen by
 * C2; this test pins the exact strings so a rename reds here before it can
 * orphan old bodies. The entity round-trip itself ({@code saveWithoutId} →
 * {@code load}, reload-keeps-grade) runs in
 * {@code CursedSpiritGradeGameTests#reloadKeepsGradeAndAttributes}, where a
 * real level exists.
 *
 * <p>Red-proof: renaming a key, or widening {@code isSpawnableLevel} to
 * grades 2–1, reds.
 */
final class CursedSpiritGradeNbtTest {
	@Test
	void keysAreExactlyTheFrozenSchema() {
		assertEquals("Grade", CursedSpiritGradeNbt.GRADE);
		assertEquals("StatHp", CursedSpiritGradeNbt.STAT_HP);
		assertEquals("StatDamage", CursedSpiritGradeNbt.STAT_DAMAGE);
		assertEquals("StatSpeed", CursedSpiritGradeNbt.STAT_SPEED);
		assertEquals("RollSeed", CursedSpiritGradeNbt.ROLL_SEED);
	}

	@Test
	void levelValiditySeparatesModelFromV1Content() {
		assertTrue(CursedSpiritGradeNbt.isValidLevel(5));
		assertTrue(CursedSpiritGradeNbt.isValidLevel(4));
		assertTrue(CursedSpiritGradeNbt.isValidLevel(3));
		assertTrue(CursedSpiritGradeNbt.isValidLevel(2));
		assertTrue(CursedSpiritGradeNbt.isValidLevel(1));
		assertFalse(CursedSpiritGradeNbt.isValidLevel(0));
		assertFalse(CursedSpiritGradeNbt.isValidLevel(6));
		assertFalse(CursedSpiritGradeNbt.isValidLevel(-5));
		assertTrue(CursedSpiritGradeNbt.isSpawnableLevel(5));
		assertTrue(CursedSpiritGradeNbt.isSpawnableLevel(4));
		assertTrue(CursedSpiritGradeNbt.isSpawnableLevel(3));
		assertFalse(CursedSpiritGradeNbt.isSpawnableLevel(2));
		assertFalse(CursedSpiritGradeNbt.isSpawnableLevel(1));
		assertFalse(CursedSpiritGradeNbt.isSpawnableLevel(0));
	}
	@Test
	void statsInBandFollowsTheClaimedGrade() {
		CursedSpiritGradeStats mid4 = new CursedSpiritGradeStats(32.0, 7.5, 0.275);
		assertTrue(CursedSpiritGradeNbt.statsInBand(mid4, CursedSpiritGrade.GRADE_4));
		assertFalse(CursedSpiritGradeNbt.statsInBand(mid4, CursedSpiritGrade.GRADE_5));
		assertFalse(CursedSpiritGradeNbt.statsInBand(mid4, CursedSpiritGrade.GRADE_3));
		CursedSpiritGradeStats edge5 = new CursedSpiritGradeStats(20.0, 5.0, 0.25);
		assertTrue(CursedSpiritGradeNbt.statsInBand(edge5, CursedSpiritGrade.GRADE_5));
	}

	@Test
	void auraTiersOrderWithTheGrade() {
		assertEquals(0, CursedSpiritAuraRuntime.auraTier(CursedSpiritGrade.GRADE_5));
		assertEquals(1, CursedSpiritAuraRuntime.auraTier(CursedSpiritGrade.GRADE_4));
		assertEquals(2, CursedSpiritAuraRuntime.auraTier(CursedSpiritGrade.GRADE_3));
		int motes5 = CursedSpiritAuraRuntime.particlesPerTick(CursedSpiritGrade.GRADE_5);
		int motes4 = CursedSpiritAuraRuntime.particlesPerTick(CursedSpiritGrade.GRADE_4);
		int motes3 = CursedSpiritAuraRuntime.particlesPerTick(CursedSpiritGrade.GRADE_3);
		assertTrue(motes5 >= 1 && motes5 < motes4 && motes4 < motes3,
				"aura density must rise with strength: " + motes5 + "/" + motes4 + "/" + motes3);
		assertTrue(CursedSpiritAuraRuntime.primaryColorArgb(CursedSpiritGrade.GRADE_5)
				!= CursedSpiritAuraRuntime.primaryColorArgb(CursedSpiritGrade.GRADE_4));
		assertTrue(CursedSpiritAuraRuntime.primaryColorArgb(CursedSpiritGrade.GRADE_4)
				!= CursedSpiritAuraRuntime.primaryColorArgb(CursedSpiritGrade.GRADE_3));
		assertEquals(0, CursedSpiritAuraRuntime.soulFlameEveryTicks(CursedSpiritGrade.GRADE_5));
		assertEquals(0, CursedSpiritAuraRuntime.soulFlameEveryTicks(CursedSpiritGrade.GRADE_4));
		assertTrue(CursedSpiritAuraRuntime.soulFlameEveryTicks(CursedSpiritGrade.GRADE_3) > 0);
	}
}
