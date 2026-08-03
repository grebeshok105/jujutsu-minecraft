package jujutsu.mod.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

/** Shared custom animation pass retained from the former player Geo models. */
public abstract class CharacterSkinAnimationModel<A extends GeoAnimatable> extends GeoModel<A> {
	private static final String HEAD_BONE = "head";
	private static final String RIGHT_ARM_BONE = "rightArm";
	private static final String LEFT_ARM_BONE = "leftArm";
	private static final float MAX_HEAD_YAW_DEGREES = 38.0f;
	private static final float MAX_HEAD_PITCH_DEGREES = 22.0f;

	protected abstract float headLookWeight(AnimationState<A> animationState, PlayerRenderState playerState);

	protected abstract boolean actionKeyframedIsPlaying(AnimationState<A> animationState);

	@Override
	public void setCustomAnimations(AnimationState<A> animationState) {
		if (!(animationState.renderState() instanceof PlayerRenderState playerState)) {
			return;
		}
		applyVanillaArmPose(animationState, playerState);
		float weight = headLookWeight(animationState, playerState);
		if (weight > 0.01f) {
			getBone(HEAD_BONE).ifPresent(head -> applyHeadLook(head, playerState, weight));
		}
	}

	private void applyVanillaArmPose(AnimationState<A> animationState, PlayerRenderState playerState) {
		if (actionKeyframedIsPlaying(animationState)) {
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
	}

	private static void applyVanillaArmPose(GeoBone bone, ModelPart vanillaArm) {
		bone.setRotX(-vanillaArm.xRot);
		bone.setRotY(-vanillaArm.yRot);
		bone.setRotZ(vanillaArm.zRot);
		bone.resetStateChanges();
	}

	private static void applyHeadLook(GeoBone head, PlayerRenderState playerState, float weight) {
		float yawDegrees = Mth.clamp(Mth.wrapDegrees(playerState.yRot - playerState.bodyRot),
				-MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
		float pitchDegrees = Mth.clamp(playerState.xRot, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);
		head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
		head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
		head.resetStateChanges();
	}
}
