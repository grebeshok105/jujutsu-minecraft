package jujutsu.mod.cursedincident.policy;

import net.minecraft.util.RandomSource;

/** Pure pressure accumulation and spawn-probability rules. */
public final class PressurePolicy {
	public static final long TICKS_PER_DAY = 24_000L;
	public static final double PRESSURE_FOR_FULL_CHANCE = 240.0;
	public static final double MAX_BASE_CHANCE = 0.90;
	public static final double MIN_DAMPING = 0.05;

	private PressurePolicy() {
	}

	/** One pressure point per complete in-game day; negative elapsed time is ignored. */
	public static long accumulate(long elapsedTicks) {
		return Math.max(0L, elapsedTicks) / TICKS_PER_DAY;
	}

	/** Soft anti-stampede damping; there is never a hard active-zone refusal. */
	public static double damping(int activeZones) {
		int zones = Math.max(0, activeZones);
		return Math.max(MIN_DAMPING, 1.0 / (1.0 + zones));
	}

	public static double spawnChance(double pressure, int activeZones) {
		double normalized = Math.max(0.0, pressure) / PRESSURE_FOR_FULL_CHANCE;
		return Math.min(MAX_BASE_CHANCE, normalized) * damping(activeZones);
	}

	public static boolean shouldSpawn(RandomSource random, double pressure, int activeZones) {
		return random.nextDouble() < spawnChance(pressure, activeZones);
	}
}
