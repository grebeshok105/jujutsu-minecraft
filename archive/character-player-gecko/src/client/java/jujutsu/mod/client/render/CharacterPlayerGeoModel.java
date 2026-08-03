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

/**
 * Per-frame pose corrections shared by every vessel model: vanilla-equivalent held-item arm poses
 * and a clamped head look. Vessels differ only in their assets and in how strongly an action clip
 * damps the look, so those are the only two things subclasses provide.
 */
public abstract class CharacterPlayerGeoModel<A extends GeoAnimatable> extends GeoModel<A> {
	protected static final String HEAD_BONE = "head";
	protected static final String RIGHT_ARM_BONE = "rightArm";
	protected static final String LEFT_ARM_BONE = "leftArm";
	protected static final String RIGHT_ELBOW_BONE = "right_elbow";
	protected static final String LEFT_ELBOW_BONE = "left_elbow";
	// Conservative clamps: the earlier 75/45 attempt tore the head off the neck seam.
	protected static final float MAX_HEAD_YAW_DEGREES = 38.0f;
	protected static final float MAX_HEAD_PITCH_DEGREES = 22.0f;

	/** How much of the raw look angle survives this frame, 0..1. */
	protected abstract float headLookWeight(AnimationState<A> animationState, PlayerRenderState playerState);

	/** Whether a keyframed action clip currently owns the arms and head. */
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
		getBone(RIGHT_ELBOW_BONE).ifPresent(CharacterPlayerGeoModel::straightenElbow);
		getBone(LEFT_ELBOW_BONE).ifPresent(CharacterPlayerGeoModel::straightenElbow);
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

	/**
	 * Applies the look as an offset from the animated rest pose, so idle/walk head keys cannot pin
	 * yaw at zero after previous frames. {@code resetStateChanges} keeps this render-only change out
	 * of GeckoLib's next-frame reset bookkeeping.
	 */
	private static void applyHeadLook(GeoBone head, PlayerRenderState playerState, float weight) {
		float yawDegrees = Mth.clamp(Mth.wrapDegrees(playerState.yRot - playerState.bodyRot), -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
		float pitchDegrees = Mth.clamp(playerState.xRot, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);
		head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
		head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
		head.resetStateChanges();
	}
}
