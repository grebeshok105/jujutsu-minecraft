package jujutsu.mod.client.render.megumi;

import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterHeldItemLayer;

public final class MegumiHeldItemLayer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterHeldItemLayer<MegumiPlayerGeoAnimatable, R> {
	public MegumiHeldItemLayer(MegumiPlayerGeoRenderer<R> renderer) {
		super(renderer, "right_hand", "left_hand");
	}
}
