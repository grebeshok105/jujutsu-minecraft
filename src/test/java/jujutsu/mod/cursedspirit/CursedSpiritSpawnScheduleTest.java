package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Day/night schedule contract (Block 4, Step 1): the BALANCE default, the pure day window, and
 * the gate boundaries. Red-proof: forcing {@code DAY_SPAWN_CHANCE} to 0 (the documented rollback
 * switch) reddens {@code dayRollBelowChancePassesByDay}.
 */
final class CursedSpiritSpawnScheduleTest {
	@AfterEach
	void resetPin() {
		CursedSpiritSpawnSchedule.resetDayChance();
	}

	@Test
	void balanceDefaultIsPointSix() {
		assertEquals(0.6, CursedSpiritSpawnSchedule.dayChance(), 1e-12, "BALANCE start value");
		assertEquals(0.6, CursedSpiritSpawnSchedule.DAY_SPAWN_CHANCE, 1e-12, "constant matches");
	}

	@Test
	void dayWindowCoversMorningAndMidday() {
		assertTrue(CursedSpiritSpawnSchedule.isDaytime(0L), "sunrise edge is day");
		assertTrue(CursedSpiritSpawnSchedule.isDaytime(1000L), "morning is day");
		assertTrue(CursedSpiritSpawnSchedule.isDaytime(6000L), "noon is day");
		assertTrue(CursedSpiritSpawnSchedule.isDaytime(11999L), "sunset edge is day");
		assertFalse(CursedSpiritSpawnSchedule.isDaytime(12000L), "past sunset is night");
		assertFalse(CursedSpiritSpawnSchedule.isDaytime(18000L), "midnight is night");
		assertFalse(CursedSpiritSpawnSchedule.isDaytime(23999L), "pre-dawn is night");
		assertTrue(CursedSpiritSpawnSchedule.isDaytime(24000L), "cycle wraps to day");
	}

	@Test
	void dayRollBelowChancePassesByDay() {
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(true, 0.0), "roll 0 passes by day");
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(true, 0.59), "roll below 0.6 passes");
	}

	@Test
	void dayRollAtOrAboveChanceRefusesByDay() {
		assertFalse(CursedSpiritSpawnSchedule.allowsAt(true, 0.6), "roll at chance refuses");
		assertFalse(CursedSpiritSpawnSchedule.allowsAt(true, 1.0), "roll 1 refuses by day");
	}

	@Test
	void nightIgnoresTheRoll() {
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(false, 0.0), "night roll 0 allows");
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(false, 1.0), "night roll 1 allows");
	}

	@Test
	void pinForcesBothBranchesAndResets() {
		CursedSpiritSpawnSchedule.pinDayChance(1.0);
		assertEquals(1.0, CursedSpiritSpawnSchedule.dayChance(), 1e-12, "pin 1.0 reads back");
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(true, 0.999), "pinned 1.0 allows by day");

		CursedSpiritSpawnSchedule.pinDayChance(0.0);
		assertFalse(CursedSpiritSpawnSchedule.allowsAt(true, 0.0), "pinned 0.0 refuses by day");
		assertTrue(CursedSpiritSpawnSchedule.allowsAt(false, 1.0), "pinned 0.0 still allows night");

		CursedSpiritSpawnSchedule.resetDayChance();
		assertEquals(CursedSpiritSpawnSchedule.DAY_SPAWN_CHANCE, CursedSpiritSpawnSchedule.dayChance(),
				1e-12, "reset restores the BALANCE constant");
	}

	@Test
	void pinRejectsOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> CursedSpiritSpawnSchedule.pinDayChance(-0.1),
				"negative pin rejected");
		assertThrows(IllegalArgumentException.class, () -> CursedSpiritSpawnSchedule.pinDayChance(1.1),
				"above-one pin rejected");
		assertThrows(IllegalArgumentException.class,
				() -> CursedSpiritSpawnSchedule.pinDayChance(Double.NaN), "NaN pin rejected");
	}

	@Test
	void daylightAllowsCombinesClockAndRoll() {
		assertTrue(CursedSpiritSpawnSchedule.daylightAllows(6000L, 0.0), "noon roll 0 allows");
		assertFalse(CursedSpiritSpawnSchedule.daylightAllows(6000L, 1.0), "noon roll 1 refuses");
		assertTrue(CursedSpiritSpawnSchedule.daylightAllows(18000L, 1.0), "midnight roll 1 allows");
	}
}
