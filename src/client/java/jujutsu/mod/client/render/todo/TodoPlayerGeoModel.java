package jujutsu.mod.client.render.todo;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.CharacterPlayerGeoModel;

public final class TodoPlayerGeoModel extends CharacterPlayerGeoModel<TodoPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("todo/todo_aoi");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/character/todo_aoi.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("todo/todo_aoi");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(TodoPlayerGeoAnimatable animatable) {
		return ANIMATIONS;
	}

	@Override
	protected float headLookWeight(AnimationState<TodoPlayerGeoAnimatable> animationState, PlayerRenderState playerState) {
		return TodoPlayerGeoAnimatable.headLookWeight(animationState, playerState);
	}

	@Override
	protected boolean actionKeyframedIsPlaying(AnimationState<TodoPlayerGeoAnimatable> animationState) {
		return TodoPlayerGeoAnimatable.actionKeyframedIsPlaying(animationState);
	}
}
