package jujutsu.mod.client.render.todo;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.todo.TodoStoneEntity;

/** Resource bindings for Todo's ordinary flying pebble. */
public final class TodoStoneModel extends GeoModel<TodoStoneEntity> {
	private static final ResourceLocation MODEL = JujutsuMod.id("todo/todo_stone");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/todo_stone.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("todo/todo_stone");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(TodoStoneEntity animatable) {
		return ANIMATIONS;
	}
}
