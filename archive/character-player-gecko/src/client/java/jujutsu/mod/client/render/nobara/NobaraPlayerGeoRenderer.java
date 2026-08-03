package jujutsu.mod.client.render.nobara;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterPlayerGeoRenderer;

public final class NobaraPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends CharacterPlayerGeoRenderer<NobaraPlayerGeoAnimatable, R> {
	public NobaraPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new NobaraPlayerGeoModel(), NobaraPlayerGeoAnimatable.INSTANCE);
		addRenderLayer(new NobaraHeldItemLayer<>(this));
		withScale(0.94f, 0.94f);
	}
}
