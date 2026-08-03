package jujutsu.mod.client.render.todo;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterPlayerGeoRenderer;

public final class TodoPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterPlayerGeoRenderer<TodoPlayerGeoAnimatable, R> {
	public TodoPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new TodoPlayerGeoModel(), TodoPlayerGeoAnimatable.INSTANCE);
		addRenderLayer(new TodoHeldItemLayer<>(this));
		// Model bind pose is ~36.75u; slight scale matches player footprint like Nobara.
		withScale(0.96f, 0.96f);
	}
}
