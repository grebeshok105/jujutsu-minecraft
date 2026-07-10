package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class ProjectJjkHammerItem extends Item {
	public ProjectJjkHammerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof net.minecraft.server.level.ServerLevel && player instanceof ServerPlayer serverPlayer) {
			if (player.isShiftKeyDown()) {
				ProjectJjkStrawDollRuntime.tryStart(serverPlayer, stack, hand);
			} else {
				ProjectJjkNobaraRuntime.launchHairpin(serverPlayer, stack, hand, false);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
