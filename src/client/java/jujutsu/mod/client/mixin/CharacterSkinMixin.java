package jujutsu.mod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

/**
 * Replaces the vanilla player skin (including first-person hands) for selected vessels.
 * Third-person GeckoLib models keep their own geo textures; this drives FP + vanilla skin paths.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class CharacterSkinMixin {
	private static final ResourceLocation NOBARA_SKIN = JujutsuMod.id("textures/entity/character/nobara.png");
	private static final ResourceLocation TODO_SKIN = JujutsuMod.id("textures/entity/character/todo.png");

	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void jujutsumod$replaceCharacterSkin(CallbackInfoReturnable<PlayerSkin> cir) {
		AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
		ClientCharacterSelectionManager.Selection selection = ClientCharacterSelectionManager.selection(player.getUUID());
		if (selection == null) {
			return;
		}
		ResourceLocation skin = switch (selection.character()) {
			case NOBARA -> NOBARA_SKIN;
			case TODO -> TODO_SKIN;
			case NONE -> null;
		};
		if (skin == null) {
			return;
		}

		PlayerSkin original = cir.getReturnValue();
		cir.setReturnValue(new PlayerSkin(
				skin,
				"",
				original.capeTexture(),
				original.elytraTexture(),
				selection.model(),
				true
		));
	}
}
