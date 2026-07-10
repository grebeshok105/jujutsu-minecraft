package jujutsu.mod.character.nobara.projectjjk;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.combat.CombatStagger;
import jujutsu.mod.registry.JujutsuItems;

public final class NobaraActionGuard {
	private NobaraActionGuard() {}

	public static void register() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (player instanceof ServerPlayer serverPlayer && (isStaggered(serverPlayer) || isHammer(player.getItemInHand(hand)))) return InteractionResult.FAIL;
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				player instanceof ServerPlayer serverPlayer && isStaggered(serverPlayer) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				player instanceof ServerPlayer serverPlayer && isStaggered(serverPlayer) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				player instanceof ServerPlayer serverPlayer && isStaggered(serverPlayer) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				player instanceof ServerPlayer serverPlayer && isStaggered(serverPlayer) ? InteractionResult.FAIL : InteractionResult.PASS);
	}

	private static boolean isStaggered(ServerPlayer player) {
		return CombatStagger.GLOBAL.isStaggered(player.getUUID(), player.level().getGameTime());
	}

	private static boolean isHammer(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}
}
