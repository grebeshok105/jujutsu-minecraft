package jujutsu.mod.client.render.nobara;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

public final class NobaraPlayerGeoModel extends GeoModel<NobaraPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("projectjjk/nobara_kugisaki");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/projectjjk/entity/npcs/nobara_kugisaki.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("projectjjk/npc");
	private static final String HEAD_BONE = "head";
	private static final String RIGHT_ARM_BONE = "rightArm";
	private static final String LEFT_ARM_BONE = "leftArm";
	private static final String RIGHT_ELBOW_BONE = "right_elbow";
	private static final String LEFT_ELBOW_BONE = "left_elbow";
	private static final float MAX_HEAD_YAW_DEGREES = 38.0f;
	private static final float MAX_HEAD_PITCH_DEGREES = 22.0f;

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(NobaraPlayerGeoAnimatable animatable) {
		return ANIMATIONS;
	}

	@Override
	public void setCustomAnimations(AnimationState<NobaraPlayerGeoAnimatable> animationState) {
		if (!(animationState.renderState() instanceof PlayerRenderState playerState)) {
			return;
		}
		applyVanillaArmPose(animationState, playerState);
		float weight = NobaraPlayerGeoAnimatable.headLookWeight(animationState, playerState);
		if (weight > 0.01f) {
			getBone(HEAD_BONE).ifPresent(head -> applyHeadLook(head, playerState, weight));
		}
	}

	private void applyVanillaArmPose(AnimationState<NobaraPlayerGeoAnimatable> animationState, PlayerRenderState playerState) {
		if (NobaraPlayerGeoAnimatable.headKeyframedActionIsPlaying(animationState)) {
			return;
		}
		if (!playerState.isUsingItem
				&& playerState.rightArmPose == HumanoidModel.ArmPose.EMPTY
				&& playerState.leftArmPose == HumanoidModel.ArmPose.EMPTY) {
			return;
		}

		HumanoidModel<?> poseModel = (HumanoidModel<?>) animationState.renderState()
				.getOrDefaultGeckolibData(DataTickets.HUMANOID_MODEL, null);
		if (poseModel == null) {
			return;
		}

		getBone(RIGHT_ARM_BONE).ifPresent(bone -> applyVanillaArmPose(bone, poseModel.rightArm));
		getBone(LEFT_ARM_BONE).ifPresent(bone -> applyVanillaArmPose(bone, poseModel.leftArm));
		getBone(RIGHT_ELBOW_BONE).ifPresent(NobaraPlayerGeoModel::straightenElbow);
		getBone(LEFT_ELBOW_BONE).ifPresent(NobaraPlayerGeoModel::straightenElbow);
	}

	private static void applyVanillaArmPose(GeoBone bone, ModelPart vanillaArm) {
		bone.setRotX(-vanillaArm.xRot);
		bone.setRotY(-vanillaArm.yRot);
		bone.setRotZ(vanillaArm.zRot);
		bone.resetStateChanges();
	}

	private static void straightenElbow(GeoBone bone) {
		bone.setRotX(0.0f);
		bone.setRotY(0.0f);
		bone.setRotZ(0.0f);
		bone.resetStateChanges();
	}

	private static void applyHeadLook(GeoBone head, PlayerRenderState playerState, float weight) {
		float yawDegrees = Mth.clamp(Mth.wrapDegrees(playerState.yRot - playerState.bodyRot), -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
		float pitchDegrees = Mth.clamp(playerState.xRot, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);
		head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
		head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
		head.resetStateChanges();
	}
}
