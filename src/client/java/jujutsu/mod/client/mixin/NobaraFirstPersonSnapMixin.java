package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxFirstPersonChannel;

@Mixin(ItemInHandRenderer.class)
public abstract class NobaraFirstPersonSnapMixin {
	@Unique
	private boolean jujutsumod$snapTransformPushed;

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"))
	private void jujutsumod$applySnapHandTransform(float partialTick, PoseStack matrices, MultiBufferSource.BufferSource buffer, LocalPlayer player, int combinedLight, CallbackInfo ci) {
		VfxFirstPersonChannel.Pose pose = VfxDirector.firstPersonPose();
		if (pose == null) {
			jujutsumod$snapTransformPushed = false;
			return;
		}
		HumanoidArm arm = player.getMainArm();
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		matrices.pushPose();
		jujutsumod$snapTransformPushed = true;
		matrices.translate(pose.translateX() * side, pose.translateY(), pose.translateZ());
		matrices.mulPose(Axis.XP.rotationDegrees(pose.rotateX()));
		matrices.mulPose(Axis.ZP.rotationDegrees(side * pose.rotateZ()));
		matrices.mulPose(Axis.YP.rotationDegrees(side * pose.rotateY()));
	}

	@Inject(method = "renderHandsWithItems", at = @At("RETURN"))
	private void jujutsumod$restoreSnapHandTransform(float partialTick, PoseStack matrices, MultiBufferSource.BufferSource buffer, LocalPlayer player, int combinedLight, CallbackInfo ci) {
		if (jujutsumod$snapTransformPushed) {
			matrices.popPose();
			jujutsumod$snapTransformPushed = false;
		}
	}
}
