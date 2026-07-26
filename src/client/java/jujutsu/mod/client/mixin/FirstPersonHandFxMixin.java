package jujutsu.mod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.VfxFirstPersonChannel;

/**
 * First-person hand transforms driven by VFX Core, shared by every vessel.
 * <ul>
 *   <li>SNAP — whole-stack transform for Nobara (vanilla path continues).</li>
 *   <li>CLAP — Todo: cancel vanilla hand selection (it only draws the main arm when empty) and
 *       draw BOTH arms with equip/swing 0 plus a small on-screen meet offset.</li>
 * </ul>
 * Clap offsets stay small: parent rotations multiply {@code renderPlayerArm}'s large fixed translates.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonHandFxMixin {
	@Unique
	private boolean jujutsumod$snapTransformPushed;
	@Unique
	private boolean jujutsumod$drawingClapArms;
	@Unique
	private float jujutsumod$clapFrameProgress = -1.0f;

	@Shadow
	private void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int combinedLight, float equippedProgress, float swingProgress, HumanoidArm side) {
	}

	@Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
	private void jujutsumod$applyFirstPersonHandFx(
			float partialTick,
			PoseStack matrices,
			MultiBufferSource.BufferSource buffer,
			LocalPlayer player,
			int combinedLight,
			CallbackInfo ci
	) {
		VfxDirector.expireFirstPerson();
		VfxFirstPersonChannel.Style style = VfxDirector.firstPersonStyle();
		if (style == VfxFirstPersonChannel.Style.CLAP) {
			// Vanilla empty-hand path only ever draws the main arm. Own both arms so the off-hand
			// is guaranteed on-screen at the same height/base pose as the main arm.
			try {
				jujutsumod$drawBothClapArms(matrices, buffer, player, combinedLight);
			} finally {
				// Vanilla renderHandsWithItems ends with endBatch(). Arms are only submitted to the GPU
				// there, and RenderType.draw samples the model-view at draw time, so skipping it defers
				// the draw past GameRenderer's popMatrix and adds a camera-orientation rotation.
				buffer.endBatch();
			}
			ci.cancel();
			return;
		}
		if (style != VfxFirstPersonChannel.Style.SNAP) {
			jujutsumod$snapTransformPushed = false;
			return;
		}
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
	private void jujutsumod$restoreSnapHandTransform(
			float partialTick,
			PoseStack matrices,
			MultiBufferSource.BufferSource buffer,
			LocalPlayer player,
			int combinedLight,
			CallbackInfo ci
	) {
		if (jujutsumod$snapTransformPushed) {
			matrices.popPose();
			jujutsumod$snapTransformPushed = false;
		}
	}

	@Unique
	private void jujutsumod$drawBothClapArms(
			PoseStack matrices,
			MultiBufferSource.BufferSource buffer,
			LocalPlayer player,
			int combinedLight
	) {
		if (player == null || player.isSpectator() || player.isInvisible()) {
			return;
		}
		jujutsumod$drawingClapArms = true;
		// One progress sample for the whole frame: the channel's clock is wall-time, so sampling per
		// arm puts the two hands on different instants and can expire the pose between them.
		jujutsumod$clapFrameProgress = VfxDirector.firstPersonProgress();
		try {
			// Always draw right then left in fixed order so poses are independent of main-hand setting.
			jujutsumod$drawOneClapArm(matrices, buffer, combinedLight, HumanoidArm.RIGHT);
			jujutsumod$drawOneClapArm(matrices, buffer, combinedLight, HumanoidArm.LEFT);
		} finally {
			jujutsumod$drawingClapArms = false;
			jujutsumod$clapFrameProgress = -1.0f;
		}
	}

	@Unique
	private void jujutsumod$drawOneClapArm(
			PoseStack matrices,
			MultiBufferSource buffer,
			int combinedLight,
			HumanoidArm arm
	) {
		// renderPlayerArm mutates the caller's stack and never restores it, so each arm owns its frame.
		matrices.pushPose();
		try {
			// equip=0, swing=0: same rest base for both arms (vanilla empty-hand rest).
			renderPlayerArm(matrices, buffer, combinedLight, 0.0f, 0.0f, arm);
		} finally {
			matrices.popPose();
		}
	}

	/** Kill attack/item residual while clapping so both arms share the same base pose. */
	@ModifyVariable(method = "renderPlayerArm", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float jujutsumod$zeroEquipDuringClap(float equippedProgress) {
		return jujutsumod$clapActive() ? 0.0f : equippedProgress;
	}

	@ModifyVariable(method = "renderPlayerArm", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private float jujutsumod$zeroSwingDuringClap(float swingProgress) {
		return jujutsumod$clapActive() ? 0.0f : swingProgress;
	}

	@Inject(method = "renderPlayerArm", at = @At("HEAD"))
	private void jujutsumod$applyClapArmTransform(
			PoseStack matrices,
			MultiBufferSource buffer,
			int combinedLight,
			float equippedProgress,
			float swingProgress,
			HumanoidArm arm,
			CallbackInfo ci
	) {
		if (!jujutsumod$clapActive()) {
			return;
		}
		VfxFirstPersonChannel.Pose pose = VfxDirector.firstPersonClapArmPose(arm, jujutsumod$clapFrameProgress);
		if (pose == null) {
			return;
		}
		float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
		// Transform only: jujutsumod$drawOneClapArm owns the matching push/pop for this arm.
		// Side-mirrored meet: both palms travel toward center and slightly up into the FOV.
		matrices.translate(pose.translateX() * side, pose.translateY(), pose.translateZ());
		matrices.mulPose(Axis.XP.rotationDegrees(pose.rotateX()));
		matrices.mulPose(Axis.YP.rotationDegrees(side * pose.rotateY()));
		matrices.mulPose(Axis.ZP.rotationDegrees(side * pose.rotateZ()));
	}

	@Unique
	private boolean jujutsumod$clapActive() {
		return jujutsumod$drawingClapArms
				|| VfxDirector.firstPersonStyle() == VfxFirstPersonChannel.Style.CLAP;
	}
}
