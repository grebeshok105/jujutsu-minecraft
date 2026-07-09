package jujutsu.mod.client.render.nobara;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class NobaraPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends GeoReplacedEntityRenderer<NobaraPlayerGeoAnimatable, AbstractClientPlayer, R> {
	public NobaraPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new NobaraPlayerGeoModel(), NobaraPlayerGeoAnimatable.INSTANCE);
		withScale(0.94f, 0.94f);
	}

	public void renderNobara(PlayerRenderState state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		render(cast(state), matrices, consumers, packedLight);
	}

	@SuppressWarnings("unchecked")
	private R cast(PlayerRenderState state) {
		return (R) (Object) state;
	}
}
