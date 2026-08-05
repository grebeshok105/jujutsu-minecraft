package jujutsu.mod.client.render.todo;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterHeldItemLayer;

/** Attaches held items to Todo's hand bones (left_hand / right_hand). */
public final class TodoHeldItemLayer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterHeldItemLayer<TodoPlayerGeoAnimatable, R> {
	public TodoHeldItemLayer(TodoPlayerGeoRenderer<R> renderer) {
		super(renderer, "right_hand", "left_hand");
	}
}
