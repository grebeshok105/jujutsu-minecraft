package jujutsu.mod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

@Mixin(PlayerRenderer.class)
public abstract class NobaraPlayerRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("RETURN"))
	private void jujutsumod$rememberNobaraPlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
		ClientCharacterSelectionManager.rememberEntity(player, partialTick);
	}
}
