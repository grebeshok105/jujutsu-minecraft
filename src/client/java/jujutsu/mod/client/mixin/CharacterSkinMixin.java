package jujutsu.mod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

@Mixin(AbstractClientPlayer.class)
public abstract class CharacterSkinMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$replaceCharacterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
		ClientCharacterSelectionManager.Selection selection = ClientCharacterSelectionManager.selection(player.getUUID());
		if (selection == null || selection.character() != JujutsuCharacter.NOBARA) {
			return;
		}

		PlayerSkin original = cir.getReturnValue();
		cir.setReturnValue(new PlayerSkin(
				JujutsuMod.id("textures/entity/character/nobara.png"),
				"",
				original.capeTexture(),
				original.elytraTexture(),
				selection.model(),
				true
		));
	}
}
