package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;

/**
 * A GeckoLib replaced-player renderer that can take over vanilla player rendering for one vessel.
 * Implemented per character and resolved through {@link CharacterGeoRenderers}.
 */
public interface CharacterGeoRenderer {
	/**
	 * Draws the vessel in place of the vanilla player model.
	 *
	 * @return {@code true} when this renderer drew the player and the vanilla path must be cancelled
	 */
	boolean renderCharacter(AbstractClientPlayer player, PlayerRenderState state, float partialTick,
			PoseStack matrices, MultiBufferSource consumers, int packedLight);
}
