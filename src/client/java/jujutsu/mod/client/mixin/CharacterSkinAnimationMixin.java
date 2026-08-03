package jujutsu.mod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.render.CharacterSkinAnimationRenderer;
import jujutsu.mod.client.render.CharacterSkinAnimationState;

/** Applies vessel GeckoLib poses to the live vanilla player model without replacing its render path. */
@Mixin(LivingEntityRenderer.class)
public abstract class CharacterSkinAnimationMixin {
	@Shadow
	protected EntityModel<?> model;

	@Unique
	private CharacterSkinAnimationState jujutsumod$skinAnimationState;

	@Inject(
			method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)V",
					shift = At.Shift.AFTER))
	private void jujutsumod$applySkinAnimation(LivingEntityRenderState state, PoseStack matrices,
			MultiBufferSource consumers, int packedLight, CallbackInfo ci) {
		if (!(model instanceof PlayerModel playerModel)
				|| !(state instanceof PlayerRenderState playerState)
				|| playerState.isSpectator) {
			return;
		}

		ClientCharacterSelectionManager.RenderContext context =
				ClientCharacterSelectionManager.renderContextByEntityId(playerState.id);
		if (context == null) {
			return;
		}
		AbstractClientPlayer player = context.player();
		if (player == null) {
			return;
		}

		jujutsumod$skinAnimationState = CharacterSkinAnimationRenderer.apply(
				player, playerState, playerModel, context.partialTick(), packedLight);
	}

	@WrapMethod(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
	private void jujutsumod$restoreSkinAnimation(LivingEntityRenderState state, PoseStack matrices,
			MultiBufferSource consumers, int packedLight, Operation<Void> original) {
		float renderScale = state instanceof PlayerRenderState playerState
				? CharacterSkinAnimationRenderer.renderScale(playerState)
				: 1.0f;
		boolean scaled = renderScale != 1.0f;
		if (scaled) {
			matrices.pushPose();
			matrices.scale(renderScale, renderScale, renderScale);
		}
		try {
			original.call(state, matrices, consumers, packedLight);
		} finally {
			if (scaled) {
				matrices.popPose();
			}
			CharacterSkinAnimationState animationState = jujutsumod$skinAnimationState;
			jujutsumod$skinAnimationState = null;
			if (animationState != null) {
				animationState.close();
			}
		}
	}
}
