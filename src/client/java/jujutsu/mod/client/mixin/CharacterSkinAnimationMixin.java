package jujutsu.mod.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
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
import jujutsu.mod.client.render.HiddenBodyRenderGate;
import jujutsu.mod.client.render.ShadowBodySink;
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
		// Megumi's shadow move hides a submerged body entirely (model, layers, and name tag in one
		// pass). The server's vanilla invisible flag is the primary hide; this gate covers the sink
		// window, the local player's third-person view, and creative/spectator peeks. Entries decay
		// in a few ticks, so a lost cue packet fails open: the body becomes visible again.
		if (state instanceof PlayerRenderState playerState
				&& HiddenBodyRenderGate.isHidden(playerState.id)) {
			CharacterSkinAnimationState stale = jujutsumod$skinAnimationState;
			jujutsumod$skinAnimationState = null;
			if (stale != null) {
				stale.close();
			}
			return;
		}
		float sinkOffsetY = 0.0f;
		float renderScale = 1.0f;
		if (state instanceof PlayerRenderState playerState) {
			renderScale = CharacterSkinAnimationRenderer.renderScale(playerState);
			// Megumi's shadow dive lowers the whole body (model, layers, and name tag ride the same
			// pose) a full standing height: the sink eases 0→1, the emerge plays back 1→0 over its
			// own window. Bodies neither sinking nor emerging take the exact original path below.
			sinkOffsetY = jujutsumod$sinkOffsetY(playerState);
		}
		boolean transformed = renderScale != 1.0f || sinkOffsetY != 0.0f;
		if (transformed) {
			matrices.pushPose();
			if (renderScale != 1.0f) {
				matrices.scale(renderScale, renderScale, renderScale);
			}
			if (sinkOffsetY != 0.0f) {
				matrices.translate(0.0f, sinkOffsetY, 0.0f);
			}
		}
		try {
			original.call(state, matrices, consumers, packedLight);
		} finally {
			if (transformed) {
				matrices.popPose();
			}
			CharacterSkinAnimationState animationState = jujutsumod$skinAnimationState;
			jujutsumod$skinAnimationState = null;
			if (animationState != null) {
				animationState.close();
			}
		}
	}

	/** How far a sunk body drops below its feet: a full standing height plus a little headroom. */
	@Unique
	private static final float JUJUTSUMOD_SINK_DEPTH_BLOCKS = 1.9f;

	/** Negative Y offset in blocks for a body diving into or rising out of the shadow; 0 when idle. */
	@Unique
	private float jujutsumod$sinkOffsetY(PlayerRenderState state) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null) {
			return 0.0f;
		}
		long gameTime = client.level.getGameTime();
		float progress = ShadowBodySink.sinkProgress(state.id, gameTime);
		if (progress >= 0.0f) {
			return -JUJUTSUMOD_SINK_DEPTH_BLOCKS * ShadowBodySink.smoothstep(progress);
		}
		progress = ShadowBodySink.emergeProgress(state.id, gameTime);
		if (progress >= 0.0f) {
			return -JUJUTSUMOD_SINK_DEPTH_BLOCKS * ShadowBodySink.smoothstep(progress);
		}
		return 0.0f;
	}
}
