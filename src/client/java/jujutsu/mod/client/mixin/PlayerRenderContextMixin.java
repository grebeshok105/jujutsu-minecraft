package jujutsu.mod.client.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;

/**
 * Caches the live player entity and partial tick behind the render state, for every player.
 * Not vessel-specific: {@link PlayerRenderState} alone cannot reach the entity GeckoLib needs.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRenderContextMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("RETURN"))
	private void jujutsumod$rememberPlayerRenderContext(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
		ClientCharacterSelectionManager.rememberEntity(player, partialTick);
	}
}
