package jujutsu.mod.character.megumi;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import jujutsu.mod.JujutsuMod;

/**
 * Block tags the shikigami treat specially.
 */
public final class MegumiShikigamiTags {
	private MegumiShikigamiTags() {}

	/**
	 * What Max Elephant's footprint is allowed to break. An <em>allowlist</em> on purpose: the body
	 * walks through bases, so the list decides what counts as litter (dirt, crops, glass) and what
	 * counts as property (chests, ores, anything functional — none of which are members).
	 */
	public static final TagKey<Block> DESTRUCTIBLE_BY_SHIKIGAMI = TagKey.create(
			Registries.BLOCK,
			JujutsuMod.id("destructible_by_shikigami"));

	public static boolean breaksAllowed(BlockState state) {
		return state.is(DESTRUCTIBLE_BY_SHIKIGAMI);
	}
}
