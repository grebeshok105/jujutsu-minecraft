package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.fx.FpSnapAnimator;

@Mixin(ItemInHandRenderer.class)
public abstract class NobaraFirstPersonSnapMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$renderSnapHands(float partialTick, PoseStack matrices, MultiBufferSource.BufferSource buffer, LocalPlayer player, int combinedLight, CallbackInfo ci) {
		FpSnapAnimator.Pose pose = FpSnapAnimator.currentPose();
		if (pose == null) {
			return;
		}
		renderSnapArm(player, pose, matrices, buffer, combinedLight);
		buffer.endBatch();
		ci.cancel();
	}

	private void renderSnapArm(LocalPlayer player, FpSnapAnimator.Pose pose, PoseStack matrices, MultiBufferSource.BufferSource buffer, int combinedLight) {
		HumanoidArm arm = player.getMainArm();
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		matrices.pushPose();
		matrices.translate(pose.translate().x() * side, pose.translate().y(), pose.translate().z());
		matrices.mulPose(Axis.XP.rotationDegrees(pose.rotate().x()));
		matrices.mulPose(Axis.ZP.rotationDegrees(side * pose.rotate().z()));
		matrices.mulPose(Axis.YP.rotationDegrees(side * pose.rotate().y()));
		PlayerRenderer renderer = (PlayerRenderer) minecraft.getEntityRenderDispatcher().getRenderer(player);
		PlayerSkin skin = player.getSkin();
		boolean slim = skin.model() == PlayerSkin.Model.SLIM;
		if (arm == HumanoidArm.RIGHT) {
			renderer.renderRightHand(matrices, buffer, combinedLight, skin.texture(), slim);
		} else {
			renderer.renderLeftHand(matrices, buffer, combinedLight, skin.texture(), slim);
		}
		matrices.popPose();
	}
}
