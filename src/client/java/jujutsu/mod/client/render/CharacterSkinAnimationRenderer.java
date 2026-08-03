package jujutsu.mod.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import jujutsu.mod.client.character.CharacterClientDefinition;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;

/** Shared selection-to-animation dispatch for vanilla player rendering. */
public final class CharacterSkinAnimationRenderer {
	private CharacterSkinAnimationRenderer() {}

	public static CharacterSkinAnimationState apply(AbstractClientPlayer player, PlayerRenderState renderState,
			PlayerModel playerModel, float partialTick, int packedLight) {
		ClientCharacterSelectionManager.Selection selection =
				ClientCharacterSelectionManager.selectionByEntityId(renderState.id);
		if (selection == null) {
			return null;
		}
		CharacterClientDefinition definition = JujutsuCharacterClients.definition(selection.character());
		CharacterSkinAnimation animation = definition.skinAnimation();
		return animation == null ? null : animation.apply(player, renderState, playerModel, partialTick, packedLight);
	}
}
