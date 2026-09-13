package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * Night-vs-day spawn-rate ratio (R38): both gate branches sampled over one shared roll stream —
 * night allows strictly more often than day. About the <em>ratio</em>, not the constant: the
 * 0.6 pin lives in {@link CursedSpiritSpawnScheduleTest}, here the day rate only has to track
 * the live chance while the night-minus-day delta stays positive.
 *
 * <p>Red-proof: pinning the day chance to 1.0 zeroes the delta and reddens the ratio assert;
 * refusing the night branch does the same.
 */
final class CursedSpiritSpawnRateTest {
	private static final int SAMPLES = 20000;
	private static final long NOON_TICKS = 6000L;
	private static final long MIDNIGHT_TICKS = 18000L;

	@Test
	void nightAllowsMoreOftenThanDayOverSharedRolls() {
		Random rolls = new Random(0xC0FFEE);
		int dayAllowed = 0;
		int nightAllowed = 0;
		for (int i = 0; i < SAMPLES; i++) {
			double roll = rolls.nextDouble();
			if (CursedSpiritSpawnSchedule.daylightAllows(NOON_TICKS, roll)) {
				dayAllowed++;
			}
			if (CursedSpiritSpawnSchedule.daylightAllows(MIDNIGHT_TICKS, roll)) {
				nightAllowed++;
			}
		}
		double dayRate = (double) dayAllowed / SAMPLES;
		double nightRate = (double) nightAllowed / SAMPLES;
		assertEquals(1.0, nightRate, 1e-12, "night allows every roll");
		assertEquals(CursedSpiritSpawnSchedule.dayChance(), dayRate, 0.05,
				"day rate tracks the live chance over " + SAMPLES + " rolls");
		assertTrue(nightRate - dayRate > 0.0,
				"night strictly more frequent: night=" + nightRate + " day=" + dayRate);
	}
}
