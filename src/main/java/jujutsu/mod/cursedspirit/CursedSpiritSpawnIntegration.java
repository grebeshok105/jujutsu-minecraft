package jujutsu.mod.cursedspirit;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biomes;
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
	 * {@code PEACEFUL} refusal, plus {@link CursedSpiritSpawnRules#belowLocalCap} for
	 * {@code EntitySpawnReason.NATURAL} — spawners, eggs and commands bypass the crowd cap).
	 */
	public static void register() {
		for (SpawnRow row : spawnRows()) {
			BiomeModifications.addSpawn(spiritBiomeSelector(), MobCategory.MONSTER,
					row.type(), row.weight(), row.minGroupSize(), row.maxGroupSize());
		}
	}

	/**
	 * Narrower than {@code foundInOverworld()} (#93): the vanilla monster set never lands in
	 * mushroom fields or the deep dark (both ship empty spawn lists), and a {@code NO_RESTRICTIONS}
	 * spirit on an ocean/river floor spawns underwater and floats up — so those biomes are out.
	 * Kept as a method so a single seam documents the whole exclusion set.
	 */
	static Predicate<BiomeSelectionContext> spiritBiomeSelector() {
		return BiomeSelectors.foundInOverworld()
				.and(BiomeSelectors.excludeByKey(Biomes.MUSHROOM_FIELDS, Biomes.DEEP_DARK))
				.and(context -> !context.hasTag(BiomeTags.IS_OCEAN)
						&& !context.hasTag(BiomeTags.IS_RIVER));
	}
}
