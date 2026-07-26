package jujutsu.mod.client.render.todo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.client.render.CharacterGeoRenderer;

public final class TodoPlayerGeoRenderer<R extends PlayerRenderState & GeoRenderState>
		extends GeoReplacedEntityRenderer<TodoPlayerGeoAnimatable, AbstractClientPlayer, R>
		implements CharacterGeoRenderer {
	private final PlayerModel vanillaPoseModel;

	public TodoPlayerGeoRenderer(EntityRendererProvider.Context context) {
		super(context, new TodoPlayerGeoModel(), TodoPlayerGeoAnimatable.INSTANCE);
		this.vanillaPoseModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false);
		addRenderLayer(new TodoHeldItemLayer<>(this));
		// Model bind pose is ~36.75u; slight scale matches player footprint like Nobara.
		withScale(0.96f, 0.96f);
	}

	@Override
	public void addRenderData(TodoPlayerGeoAnimatable animatable, AbstractClientPlayer player, R renderState) {
		vanillaPoseModel.setupAnim(renderState);
		renderState.addGeckolibData(DataTickets.HUMANOID_MODEL, vanillaPoseModel);
	}

	@Override
	public boolean renderCharacter(AbstractClientPlayer player, PlayerRenderState state, float partialTick, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
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
