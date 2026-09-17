package jujutsu.mod.cursedincident.policy;

import net.minecraft.util.RandomSource;

/**
 * Pure cursed-pressure rules (issue #110 spec §4.2): the world accumulates pressure over
 * time; spawn probability rises with pressure and is damped by the number of active
 * zones — a soft multiplier, never a hard cap (C2).
 */
public final class PressurePolicy {

	private PressurePolicy() {
	}

	/** Pressure gained over {@code elapsedTicks} of game time (+1 per game day). */
	public static long accumulate(long elapsedTicks) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/** Spawn probability: {@code min(0.9, pressure/240) * damping(activeZones)}. */
	public static double spawnChance(double pressure, int activeZones) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/** Soft damping {@code max(0.05, 1/(1+n))} — never a refusal (C2). */
	public static double damping(int activeZones) {
		throw new UnsupportedOperationException("block-1 pending");
	}

	/** Seeded spawn decision. */
	public static boolean shouldSpawn(RandomSource random, double pressure, int activeZones) {
		throw new UnsupportedOperationException("block-1 pending");
	}
}
