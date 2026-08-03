package jujutsu.mod.client.render.nobara;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterHeldItemLayer;

/** Attaches held items to Nobara's hand bones (leftHandItem / rightHandItem). */
public final class NobaraHeldItemLayer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterHeldItemLayer<NobaraPlayerGeoAnimatable, R> {
	public NobaraHeldItemLayer(NobaraPlayerGeoRenderer<R> renderer) {
		super(renderer, "rightHandItem", "leftHandItem");
	}
}
