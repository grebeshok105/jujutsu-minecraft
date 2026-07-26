package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * Shared entry point for vessel renderers that replace the vanilla player model.
 * Subclasses only declare their model, animatable, render layers, and scale.
 */
public abstract class CharacterPlayerGeoRenderer<A extends GeoAnimatable, R extends PlayerRenderState & GeoRenderState>
		extends GeoReplacedEntityRenderer<A, AbstractClientPlayer, R>
		implements CharacterGeoRenderer {
	private final PlayerModel vanillaPoseModel;

	protected CharacterPlayerGeoRenderer(EntityRendererProvider.Context context, GeoModel<A> model, A animatable) {
		super(context, model, animatable);
		this.vanillaPoseModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
	}

	@Override
	public void addRenderData(A animatable, AbstractClientPlayer player, R renderState) {
		vanillaPoseModel.setupAnim(renderState);
		renderState.addGeckolibData(DataTickets.HUMANOID_MODEL, vanillaPoseModel);
	}

	@Override
	public final boolean renderCharacter(AbstractClientPlayer player, PlayerRenderState state, float partialTick,
			PoseStack matrices, MultiBufferSource consumers, int packedLight) {
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

	/** Unwinds anything the vessel render left behind so a bad frame cannot corrupt the shared stack. */
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
