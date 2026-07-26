package jujutsu.mod.character.todo;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The thrown Boogie Woogie marker.
 *
 * <p>Single-stack and consumed on throw, which is what lets the technique's empty-hands rule stay
 * absolute: the gate is read at swap time, and by then the hand that threw this is empty. A stackable
 * marker would leave a remainder in hand and correctly block the swap, so do not raise the stack size.
 */
public class TodoSwapMarkerItem extends Item {
	public TodoSwapMarkerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS,
				TodoProfile.MARKER_THROW_VOLUME, TodoProfile.MARKER_THROW_PITCH);
		if (!level.isClientSide) {
			TodoSwapMarkerEntity marker = new TodoSwapMarkerEntity(level, player, stack.copyWithCount(1));
			marker.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, TodoProfile.MARKER_THROW_POWER, 1.0f);
			level.addFreshEntity(marker);
		}
		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
