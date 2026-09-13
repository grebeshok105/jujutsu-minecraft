package jujutsu.mod.cursedspirit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Natural-spawn gate for the cursed-spirit tiers (D3): darkness and difficulty stay vanilla-owned
 * (the entity's {@code checkSpawnRules} override delegates to {@code super} for the light check
 * and gates {@code PEACEFUL} explicitly) — this class owns only the local crowd cap, plus the
 * Block 4 day branch that *weakens* the vanilla light conjunct by day (#82: daylight is no
 * absolute spawn ban; night stays slightly more frequent via the schedule).
 */
public final class CursedSpiritSpawnRules {
	private CursedSpiritSpawnRules() {}

	public static boolean belowCap(int nearbyCount, int cap) {
		return nearbyCount < cap;
	}

	/**
	 * Level adapter: counts live spirits within {@link CursedSpiritProfile#CROWD_RADIUS} of the
	 * candidate spot and applies the {@link CursedSpiritProfile#MAX_SPIRITS_NEARBY} cap.
	 * Non-server worlds never spawn naturally, so they pass.
	 */
	public static boolean belowLocalCap(LevelAccessor level, BlockPos pos) {
		if (level instanceof ServerLevel serverLevel) {
			int nearby = serverLevel.getEntitiesOfClass(
					CursedSpiritEntity.class, new AABB(pos).inflate(CursedSpiritProfile.CROWD_RADIUS)).size();
			return belowCap(nearby, CursedSpiritProfile.MAX_SPIRITS_NEARBY);
		}
		return true;
	}

	/**
	 * Block 4 day branch (#82, Step 2): after the vanilla light half refuses, a day roll against
	 * {@link CursedSpiritSpawnSchedule#dayChance()} may still allow the spawn. Night always
	 * allows here (the roll is skipped); the roll source is the level RNG — never a seeded
	 * stand-in, tests pin the chance instead ({@link CursedSpiritSpawnSchedule#pinDayChance}).
	 * Javap-verified on 1.21.8: {@code LevelAccessor} exposes both {@code dayTime()} and
	 * {@code getRandom()} directly.
	 */
	/**
	 * The difficulty gate as a pure predicate. It lives here rather than inline so it is testable
	 * without touching the server's difficulty: a GameTest that flips the global difficulty to
	 * PEACEFUL runs on a level shared with every other structure, and {@code Mob.checkDespawn}
	 * discards sibling arenas' Monster bodies for as long as the window is open.
	 */
	public static boolean difficultyAllows(net.minecraft.world.Difficulty difficulty) {
		return difficulty != net.minecraft.world.Difficulty.PEACEFUL;
	}

	public static boolean daylightAllows(LevelAccessor level) {
		return CursedSpiritSpawnSchedule.daylightAllows(level.dayTime(), level.getRandom().nextDouble());
	}
}
