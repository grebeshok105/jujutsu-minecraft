package jujutsu.mod.cursedincident.infection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.IncidentTemplate;
import jujutsu.mod.cursedspirit.CursedSpiritTier;

/** Pure, deterministic world-infection mappings and stage-weight policies. */
public final class InfectionPolicy {
	private InfectionPolicy() {
	}

	public static Optional<BlockState> mapBlock(BlockState state, IncidentStage stage, RandomSource random) {
		if (state == null || state.isAir() || stage == null) {
			return Optional.empty();
		}
		Block block = state.getBlock();
		BlockState mapped = null;
		if (block == Blocks.GRASS_BLOCK) {
			if (stage.atLeast(IncidentStage.CRITICAL)) {
				mapped = Blocks.SOUL_SOIL.defaultBlockState();
			} else if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = Blocks.PODZOL.defaultBlockState();
			} else if (stage.atLeast(IncidentStage.GROWING)) {
				mapped = Blocks.COARSE_DIRT.defaultBlockState();
			}
		} else if (hasTag(state, BlockTags.LEAVES) || block instanceof LeavesBlock) {
			if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = Blocks.MANGROVE_ROOTS.defaultBlockState();
			} else if (stage.atLeast(IncidentStage.GROWING)) {
				mapped = Blocks.AIR.defaultBlockState();
			}
		} else if (isVegetation(state, block)) {
			if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = Blocks.AIR.defaultBlockState();
			} else if (stage.atLeast(IncidentStage.GROWING)) {
				mapped = Blocks.DEAD_BUSH.defaultBlockState();
			}
		} else if (hasTag(state, BlockTags.LOGS)) {
			if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = strippedVariant(block);
			}
		} else if (isCraftedWood(state, block)) {
			if (stage.atLeast(IncidentStage.CRITICAL)) {
				mapped = Blocks.AIR.defaultBlockState();
			}
		} else if (isStone(state, block)) {
			if (stage.atLeast(IncidentStage.CATASTROPHIC)) {
				mapped = Blocks.GRAVEL.defaultBlockState();
			} else if (stage.atLeast(IncidentStage.CRITICAL)) {
				mapped = Blocks.COBBLESTONE.defaultBlockState();
			}
		} else if (hasTag(state, BlockTags.IMPERMEABLE) && isGlass(block)) {
			if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = Blocks.AIR.defaultBlockState();
			}
		} else if (isGlass(block)) {
			if (stage.atLeast(IncidentStage.INFESTED)) {
				mapped = Blocks.AIR.defaultBlockState();
			}
		} else if (isLight(block)) {
			if (stage.atLeast(IncidentStage.GROWING)) {
				mapped = Blocks.AIR.defaultBlockState();
			}
		}
		if (mapped == null || mapped.equals(state)) {
			return Optional.empty();
		}
		return Optional.of(mapped);
	}

	private static boolean isVegetation(BlockState state, Block block) {
		return block == Blocks.TALL_GRASS || block == Blocks.SHORT_GRASS || block == Blocks.FERN
				|| block == Blocks.LARGE_FERN || hasTag(state, BlockTags.FLOWERS) || hasTag(state, BlockTags.SAPLINGS)
				|| hasTag(state, BlockTags.CROPS) || block instanceof CropBlock;
	}

	private static boolean isCraftedWood(BlockState state, Block block) {
		return hasTag(state, BlockTags.PLANKS) || hasTag(state, BlockTags.FENCES) || block instanceof FenceBlock
				|| block instanceof StairBlock || block instanceof SlabBlock
				|| hasTag(state, BlockTags.DOORS) || hasTag(state, BlockTags.TRAPDOORS);
	}

	private static boolean isStone(BlockState state, Block block) {
		return block == Blocks.STONE || block == Blocks.DEEPSLATE || block == Blocks.ANDESITE
				|| block == Blocks.DIORITE || block == Blocks.GRANITE || block == Blocks.TUFF
				|| hasTag(state, BlockTags.BASE_STONE_OVERWORLD);
	}

	private static boolean isGlass(Block block) {
		return block == Blocks.GLASS || block == Blocks.TINTED_GLASS || block == Blocks.GLASS_PANE
				|| block == Blocks.WHITE_STAINED_GLASS || block == Blocks.ORANGE_STAINED_GLASS
				|| block == Blocks.MAGENTA_STAINED_GLASS || block == Blocks.LIGHT_BLUE_STAINED_GLASS
				|| block == Blocks.YELLOW_STAINED_GLASS || block == Blocks.LIME_STAINED_GLASS
				|| block == Blocks.PINK_STAINED_GLASS || block == Blocks.GRAY_STAINED_GLASS
				|| block == Blocks.LIGHT_GRAY_STAINED_GLASS || block == Blocks.CYAN_STAINED_GLASS
				|| block == Blocks.PURPLE_STAINED_GLASS || block == Blocks.BLUE_STAINED_GLASS
				|| block == Blocks.BROWN_STAINED_GLASS || block == Blocks.GREEN_STAINED_GLASS
				|| block == Blocks.RED_STAINED_GLASS || block == Blocks.BLACK_STAINED_GLASS;
	}
	private static boolean hasTag(BlockState state, net.minecraft.tags.TagKey<Block> tag) {
		try {
			return state.is(tag);
		} catch (IllegalStateException ignored) {
			// Unit tests may exercise the pure policy before the dynamic tag registry is bound.
			return false;
		}
	}

	private static boolean isLight(Block block) {
		return block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.LANTERN
				|| block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH || block == Blocks.SOUL_LANTERN;
	}

	private static BlockState strippedVariant(Block block) {
		if (block == Blocks.OAK_LOG) return Blocks.STRIPPED_OAK_LOG.defaultBlockState();
		if (block == Blocks.SPRUCE_LOG) return Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
		if (block == Blocks.BIRCH_LOG) return Blocks.STRIPPED_BIRCH_LOG.defaultBlockState();
		if (block == Blocks.JUNGLE_LOG) return Blocks.STRIPPED_JUNGLE_LOG.defaultBlockState();
		if (block == Blocks.ACACIA_LOG) return Blocks.STRIPPED_ACACIA_LOG.defaultBlockState();
		if (block == Blocks.DARK_OAK_LOG) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
		if (block == Blocks.MANGROVE_LOG) return Blocks.STRIPPED_MANGROVE_LOG.defaultBlockState();
		if (block == Blocks.CHERRY_LOG) return Blocks.STRIPPED_CHERRY_LOG.defaultBlockState();
		if (block == Blocks.CRIMSON_STEM) return Blocks.STRIPPED_CRIMSON_STEM.defaultBlockState();
		if (block == Blocks.WARPED_STEM) return Blocks.STRIPPED_WARPED_STEM.defaultBlockState();
		return Blocks.AIR.defaultBlockState();
	}

	/** Explicit container set: those blocks must use destroyBlock(..., true) to preserve drops. */
	public static boolean isContainer(BlockState state) {
		if (state == null) {
			return false;
		}
		Block block = state.getBlock();
		return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
				|| block == Blocks.SHULKER_BOX || block == Blocks.HOPPER || block == Blocks.DROPPER
				|| block == Blocks.DISPENSER || block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE
				|| block == Blocks.SMOKER || block == Blocks.BREWING_STAND || block == Blocks.BEACON
				|| block == Blocks.ENDER_CHEST;
	}

	public static float cullAnimalChance(IncidentStage stage) {
		if (stage == null || stage.ordinal() < IncidentStage.INFESTED.ordinal()) return 0.0f;
		return switch (stage) {
			case INFESTED -> 0.05f;
			case CRITICAL -> 0.20f;
			case CATASTROPHIC -> 0.45f;
			default -> 0.0f;
		};
	}

	/** Weighted tier table. Values are weights, not probabilities. */
	public static Map<CursedSpiritTier, Integer> spawnTableFor(IncidentTemplate template, IncidentStage stage) {
		Map<CursedSpiritTier, Integer> table = new LinkedHashMap<>();
		IncidentStage safe = stage == null ? IncidentStage.INITIAL : stage;
		int templateBias = template == null ? 0 : Math.max(0, template.weight());
		switch (safe) {
			case INITIAL -> { table.put(CursedSpiritTier.LESSER, 8 + templateBias / 20); table.put(CursedSpiritTier.COMMON, 2); table.put(CursedSpiritTier.GREATER, 0); }
			case GROWING -> { table.put(CursedSpiritTier.LESSER, 7); table.put(CursedSpiritTier.COMMON, 4); table.put(CursedSpiritTier.GREATER, 1); }
			case INFESTED -> { table.put(CursedSpiritTier.LESSER, 5); table.put(CursedSpiritTier.COMMON, 6); table.put(CursedSpiritTier.GREATER, 2); }
			case CRITICAL -> { table.put(CursedSpiritTier.LESSER, 3); table.put(CursedSpiritTier.COMMON, 7); table.put(CursedSpiritTier.GREATER, 4); }
			case CATASTROPHIC -> { table.put(CursedSpiritTier.LESSER, 2); table.put(CursedSpiritTier.COMMON, 6); table.put(CursedSpiritTier.GREATER, 7); }
		}
		return Map.copyOf(table);
	}

	public static Map<CursedSpiritTier, Integer> spawnTableFor(String templateId, IncidentStage stage) {
		return spawnTableFor((IncidentTemplate) null, stage);
	}
}
