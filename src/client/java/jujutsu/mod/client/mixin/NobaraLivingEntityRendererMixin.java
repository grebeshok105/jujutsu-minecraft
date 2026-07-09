package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.render.nobara.NobaraPlayerGeoRenderer;

@Mixin(LivingEntityRenderer.class)
public abstract class NobaraLivingEntityRendererMixin {
	@Unique
	private NobaraPlayerGeoRenderer<?> jujutsumod$nobaraRenderer;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void jujutsumod$createNobaraRenderer(EntityRendererProvider.Context context, EntityModel<?> model, float shadowRadius, CallbackInfo ci) {
		if ((Object) this instanceof PlayerRenderer) {
			jujutsumod$nobaraRenderer = new NobaraPlayerGeoRenderer<>(context);
		}
	}

	@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$renderNobaraGeo(LivingEntityRenderState state, PoseStack matrices, MultiBufferSource consumers, int packedLight, CallbackInfo ci) {
		if (!(state instanceof PlayerRenderState playerState) || jujutsumod$nobaraRenderer == null) {
			return;
		}
		ClientCharacterSelectionManager.Selection selection = ClientCharacterSelectionManager.selectionByEntityId(playerState.id);
		if (selection == null || selection.character() != JujutsuCharacter.NOBARA || playerState.isSpectator) {
			return;
		}
		if (jujutsumod$nobaraRenderer.renderNobara(playerState, matrices, consumers, packedLight)) {
			ci.cancel();
		}
	}
}
