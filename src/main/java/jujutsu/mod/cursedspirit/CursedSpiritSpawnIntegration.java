package jujutsu.mod.cursedspirit;

import java.util.List;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Natural overworld spawns for the three tiers (D3): three {@code MONSTER} rows in every
 * overworld biome, with every number read from {@link CursedSpiritProfile}.
 */
public final class CursedSpiritSpawnIntegration {
	private CursedSpiritSpawnIntegration() {}

	/**
	 * One biome-spawn row: the type plus the profile numbers it registers with.
	 */
	public record SpawnRow(EntityType<? extends CursedSpiritEntity> type, CursedSpiritTier tier,
			int weight, int minGroupSize, int maxGroupSize) {}

	/**
	 * The three registered rows, with every number read from {@link CursedSpiritProfile} —
	 * tuning happens there, never here.
	 */
	public static List<SpawnRow> spawnRows() {
		return List.of(
				spawnRow(JujutsuEntities.LESSER_CURSED_SPIRIT, CursedSpiritTier.LESSER),
				spawnRow(JujutsuEntities.CURSED_SPIRIT, CursedSpiritTier.COMMON),
				spawnRow(JujutsuEntities.GREATER_CURSED_SPIRIT, CursedSpiritTier.GREATER));
	}

	private static SpawnRow spawnRow(EntityType<? extends CursedSpiritEntity> type, CursedSpiritTier tier) {
		CursedSpiritTierStats stats = CursedSpiritProfile.of(tier);
		return new SpawnRow(type, tier, stats.spawnWeight(), stats.spawnMinGroup(), stats.spawnMaxGroup());
	}

	/**
	 * Adds the three rows to overworld {@code MONSTER} spawns. Called once from
	 * {@code CursedSpirits.registerServerHooks()} (Block 4's serialized one-line edit) — after
	 * {@code JujutsuEntities.register()}, which {@code addSpawn} requires for its id lookup.
	 *
	 * <p>Why {@code SpawnPlacements} is untouched: {@code SpawnPlacements.register} is private in
	 * 1.21.8 with no Fabric wrapper. An unregistered type keeps the vanilla defaults
	 * ({@code NO_RESTRICTIONS} + {@code MOTION_BLOCKING_NO_LEAVES}), so the natural pipeline
	 * still picks a sane surface spot, and the per-spawn gate lives in
	 * {@code CursedSpiritEntity.checkSpawnRules} (vanilla light check via {@code super}, explicit
	 * {@code PEACEFUL} refusal, plus {@link CursedSpiritSpawnRules#belowLocalCap}).
	 */
	public static void register() {
		for (SpawnRow row : spawnRows()) {
			BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), MobCategory.MONSTER,
					row.type(), row.weight(), row.minGroupSize(), row.maxGroupSize());
		}
	}
}
