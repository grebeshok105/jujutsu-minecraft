package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.registry.JujutsuItems;

public final class ProjectJjkNobaraLoadout {
	private static final int STARTER_NAILS = 16;

	private ProjectJjkNobaraLoadout() {}

	public static void ensureStarterTools(ServerPlayer player) {
		if (!hasHammer(player)) {
			giveOrDrop(player, new ItemStack(JujutsuItems.STRAW_DOLL_HAMMER));
		}
		int missingNails = missingNails(countNails(player));
		if (missingNails > 0) {
			giveOrDrop(player, new ItemStack(JujutsuItems.HAIRPIN_NAIL, missingNails));
		}
	}

	static int missingNails(int currentNails) {
		return Math.max(0, STARTER_NAILS - Math.max(0, currentNails));
	}

	private static boolean hasHammer(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER)) {
				return true;
			}
		}
		return false;
	}

	private static int countNails(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(JujutsuItems.HAIRPIN_NAIL) || stack.is(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
		boolean stored = player.getInventory().add(stack);
		if (!stored && !stack.isEmpty()) {
			player.drop(stack, false);
		}
	}
}
