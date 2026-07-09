package jujutsu.mod.client.render.nobara;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

public final class NobaraPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends GeoReplacedEntityRenderer<NobaraPlayerGeoAnimatable, AbstractClientPlayer, R> {
	public NobaraPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new NobaraPlayerGeoModel(), NobaraPlayerGeoAnimatable.INSTANCE);
		withScale(0.94f, 0.94f);
	}

	public boolean renderNobara(AbstractClientPlayer player, PlayerRenderState state, float partialTick, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		matrices.pushPose();
		Object guardPose = matrices.last();
		try {
			R geoState = cast(state);
			fillRenderState(getAnimatable(), player, geoState, partialTick);
			geoState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight);
			render(geoState, matrices, consumers, packedLight);
			return true;
		} finally {
			restorePoseStack(matrices, guardPose);
		}
	}

	private static void restorePoseStack(PoseStack matrices, Object guardPose) {
		while (!matrices.isEmpty() && matrices.last() != guardPose) {
			matrices.popPose();
		}
		if (!matrices.isEmpty()) {
			matrices.popPose();
		}
	}

	@SuppressWarnings("unchecked")
	private R cast(PlayerRenderState state) {
		return (R) (Object) state;
	}
}
