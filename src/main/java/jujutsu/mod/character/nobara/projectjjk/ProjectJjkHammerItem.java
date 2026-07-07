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
				// Straw-doll ritual: bind to a marked target, then transmit the resonant strike.
				ProjectJjkRitualRuntime.performResonance(serverPlayer, stack, hand);
			} else {
				// Standard beat: forge the Hairpin and detonate any embedded marks in range.
				ProjectJjkNobaraRuntime.launchHairpin(serverPlayer, stack, hand);
				ProjectJjkRitualRuntime.detonateMarks(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}
}
