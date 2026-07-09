package jujutsu.mod.client.render.nobara;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

public final class NobaraPlayerGeoModel extends GeoModel<NobaraPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("projectjjk/nobara_kugisaki");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/projectjjk/entity/npcs/nobara_kugisaki.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("projectjjk/npc");
	private static final String HEAD_BONE = "head";
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
		float weight = NobaraPlayerGeoAnimatable.headLookWeight(animationState, playerState);
		if (weight <= 0.01f) {
			return;
		}
		getBone(HEAD_BONE).ifPresent(head -> applyHeadLook(head, playerState, weight));
	}

	private static void applyHeadLook(GeoBone head, PlayerRenderState playerState, float weight) {
		float yawDegrees = Mth.clamp(Mth.wrapDegrees(playerState.yRot - playerState.bodyRot), -MAX_HEAD_YAW_DEGREES, MAX_HEAD_YAW_DEGREES);
		float pitchDegrees = Mth.clamp(playerState.xRot, -MAX_HEAD_PITCH_DEGREES, MAX_HEAD_PITCH_DEGREES);
		head.setRotY(head.getRotY() - yawDegrees * Mth.DEG_TO_RAD * weight);
		head.setRotX(head.getRotX() - pitchDegrees * Mth.DEG_TO_RAD * weight);
	}
}
