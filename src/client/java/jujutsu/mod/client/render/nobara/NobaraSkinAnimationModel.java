package jujutsu.mod.client.render.nobara;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.CharacterSkinAnimationModel;

public final class NobaraSkinAnimationModel extends CharacterSkinAnimationModel<NobaraPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("character_skin/nobara");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("projectjjk/npc");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return JujutsuMod.id("textures/entity/character/nobara.png");
	}

	@Override
	public ResourceLocation getAnimationResource(NobaraPlayerGeoAnimatable animatable) {
		return ANIMATIONS;
	}

	@Override
	protected float headLookWeight(AnimationState<NobaraPlayerGeoAnimatable> animationState, PlayerRenderState playerState) {
		return NobaraPlayerGeoAnimatable.headLookWeight(animationState, playerState);
	}

	@Override
	protected boolean actionKeyframedIsPlaying(AnimationState<NobaraPlayerGeoAnimatable> animationState) {
		return NobaraPlayerGeoAnimatable.headKeyframedActionIsPlaying(animationState);
	}
}
