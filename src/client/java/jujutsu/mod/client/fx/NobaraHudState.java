package jujutsu.mod.client.fx;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.registry.JujutsuItems;

/** Small client-side predicate: is the player holding any Nobara Straw Doll kit item? */
public final class NobaraHudState {
	private NobaraHudState() {}

	public static boolean holdingNobaraKit(Player player) {
		return isKit(player.getMainHandItem()) || isKit(player.getOffhandItem());
	}

	private static boolean isKit(ItemStack stack) {
		return stack.is(JujutsuItems.HAIRPIN_NAIL)
				|| stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL)
				|| stack.is(JujutsuItems.STRAW_DOLL_HAMMER)
				|| stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}
}
