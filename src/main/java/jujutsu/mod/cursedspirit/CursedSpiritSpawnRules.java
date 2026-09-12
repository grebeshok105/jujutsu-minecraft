package jujutsu.mod.cursedspirit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

/**
 * Natural-spawn gate for the cursed-spirit tiers (D3): darkness and difficulty stay vanilla-owned
 * (the entity's {@code checkSpawnRules} override delegates to {@code super} for the light check
 * and gates {@code PEACEFUL} explicitly) — this class owns only the local crowd cap.
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
}
