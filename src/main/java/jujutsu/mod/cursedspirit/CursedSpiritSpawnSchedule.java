package jujutsu.mod.cursedspirit;

/**
 * Day/night spawn-rate schedule for the cursed spirits (#82, Block 4, Step 1).
 *
 * <p>Spec: spirits appear by day AND by night; night is only slightly more frequent; PEACEFUL
 * still refuses (that gate lives in {@link CursedSpiritEntity#checkSpawnRules}). Time of day
 * never filters the tier/grade roll — it only gates frequency through {@link #allowsAt}.
 */
public final class CursedSpiritSpawnSchedule {
	private CursedSpiritSpawnSchedule() {}

	/**
	 * BALANCE: day-spawn pass chance, not design truth. Night is only slightly more frequent
	 * ("not by an order of magnitude" per spec) — tune after in-game runs; the spec does not
	 * change. Rollback switch for the whole day branch: {@code 0.0}.
	 */
	public static final double DAY_SPAWN_CHANCE = 0.6;

	/** Ticks of daylight on the 24000-tick cycle: {@code [0, 12000)}. Coarse on purpose. */
	public static final long DAY_LENGTH_TICKS = 12000L;
	/** Full day/night cycle length. */
	public static final long FULL_DAY_TICKS = 24000L;

	/**
	 * Pinned override for tests (NaN = no pin, read {@link #DAY_SPAWN_CHANCE}). Production never
	 * pins: the live roll always comes from the level RNG, never from a seeded source — "seeded
	 * level.getRandom()" would be fake determinism, so GameTests pin the chance instead.
	 */
	private static volatile double pinnedDayChance = Double.NaN;

	/** Effective day chance: the pin if set, otherwise the {@code BALANCE} constant. */
	public static double dayChance() {
		return Double.isNaN(pinnedDayChance) ? DAY_SPAWN_CHANCE : pinnedDayChance;
	}

	/**
	 * Test hook: force the day chance (0.0 = day branch always refuses, 1.0 = always allows).
	 * Always paired with {@link #resetDayChance()} in a {@code finally}.
	 */
	public static void pinDayChance(double chance) {
		if (Double.isNaN(chance) || chance < 0.0 || chance > 1.0) {
			throw new IllegalArgumentException("day chance pin must be within [0, 1]: " + chance);
		}
		pinnedDayChance = chance;
	}

	/** Test hook: drop the pin, restoring the {@code BALANCE} constant. */
	public static void resetDayChance() {
		pinnedDayChance = Double.NaN;
	}

	/**
	 * Pure day window on the cycle clock. There is no {@code Level.isDay()} on 1.21.8 (javap:
	 * {@code Level} exposes only {@code getDayTime()}/{@code getSkyDarken()}), so the window is
	 * explicit and unit-pinned instead of recalled from memory.
	 */
	public static boolean isDaytime(long dayTime) {
		long cycle = Math.floorMod(dayTime, FULL_DAY_TICKS);
		return cycle >= 0 && cycle < DAY_LENGTH_TICKS;
	}

	/**
	 * Pure gate: night always allows (roll-independent); day allows on {@code roll < dayChance()}.
	 * Boundaries: roll 0.0 passes by day at any positive chance; roll 1.0 always refuses by day.
	 */
	public static boolean allowsAt(boolean isDay, double roll) {
		if (!isDay) {
			return true;
		}
		return roll < dayChance();
	}

	/** Clock + roll combined, for the spawn-rule call sites. */
	public static boolean daylightAllows(long dayTime, double roll) {
		return allowsAt(isDaytime(dayTime), roll);
	}
}
