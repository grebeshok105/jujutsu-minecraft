package jujutsu.mod.client.render.todo;

import net.minecraft.client.player.AbstractClientPlayer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterSkinAnimationAdapter;

/** Adds Todo's deterministic locomotion variant to the shared skin bridge. */
public final class TodoSkinAnimationAdapter extends CharacterSkinAnimationAdapter<TodoPlayerGeoAnimatable> {
	public TodoSkinAnimationAdapter() {
		super(TodoPlayerGeoAnimatable.INSTANCE, new TodoSkinAnimationModel());
	}

	@Override
	protected void addSkinAnimationData(TodoPlayerGeoAnimatable animatable, AbstractClientPlayer player,
			GeoRenderState renderState) {
		renderState.addGeckolibData(TodoPlayerGeoAnimatable.LOCOMOTION_VARIANT,
				Math.floorMod(player.tickCount / 120 + player.getId(), 2));
	}
}
