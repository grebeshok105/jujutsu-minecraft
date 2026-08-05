package jujutsu.mod.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/** A vessel-owned GeckoLib animation pass applied to vanilla player geometry. */
@FunctionalInterface
public interface CharacterSkinAnimation {
	/**
	 * Evaluates the vessel's current GeckoLib pose and applies it to the already prepared player model.
	 * The returned state must be closed after vanilla finishes rendering the player.
	 */
	CharacterSkinAnimationState apply(AbstractClientPlayer player, PlayerRenderState renderState,
			PlayerModel playerModel, float partialTick, int packedLight);
}
