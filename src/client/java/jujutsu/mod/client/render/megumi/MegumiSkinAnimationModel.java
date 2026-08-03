package jujutsu.mod.client.render.megumi;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.CharacterSkinAnimationModel;

public final class MegumiSkinAnimationModel extends CharacterSkinAnimationModel<MegumiPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("character_skin/megumi");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi/megumi_fushiguro");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return JujutsuMod.id("textures/entity/character/megumi.png");
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiPlayerGeoAnimatable animatable) {
		return ANIMATIONS;
	}

	@Override
	protected float headLookWeight(AnimationState<MegumiPlayerGeoAnimatable> animationState, PlayerRenderState playerState) {
		return 0.0f;
	}

	@Override
	protected boolean actionKeyframedIsPlaying(AnimationState<MegumiPlayerGeoAnimatable> animationState) {
		return MegumiPlayerGeoAnimatable.actionKeyframedIsPlaying(animationState);
	}
}
