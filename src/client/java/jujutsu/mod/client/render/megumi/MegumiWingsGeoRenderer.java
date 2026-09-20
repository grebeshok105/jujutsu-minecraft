package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.renderer.base.GeoRenderer;
import jujutsu.mod.client.character.megumi.MegumiWingsState;
import software.bernie.geckolib.constant.DataTickets;

/** Renders the standalone wing rig while preserving the vanilla player renderer and skin. */
public final class MegumiWingsGeoRenderer
		implements GeoRenderer<MegumiWingsModel.Animatable, AbstractClientPlayer, GeoRenderState> {
	private static final float MODEL_SCALE = 0.55f;
	private static final MegumiWingsModel.Animatable ANIMATABLE = new MegumiWingsModel.Animatable();
	private final MegumiWingsModel model = new MegumiWingsModel();

	@Override
	public MegumiWingsModel getGeoModel() {
		return model;
	}

	/** Renders one owner's current phase into the already-positioned player pose stack. */
	public void render(AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource bufferSource,
			int packedLight, float partialTick, MegumiWingsState.Phase phase) {
		if (phase == null) {
			return;
		}
		GeoRenderState.Impl renderState = new GeoRenderState.Impl();
		renderState.addGeckolibData(MegumiWingsModel.PHASE, phase.wireValue());
		renderState.addGeckolibData(DataTickets.PACKED_LIGHT, packedLight);
		renderState.addGeckolibData(DataTickets.PACKED_OVERLAY, OverlayTexture.NO_OVERLAY);
		renderState.addGeckolibData(DataTickets.RENDER_COLOR, 0xFFFFFFFF);
		model.getBakedModel(model.getModelResource(renderState));
		fillRenderState(ANIMATABLE, player, renderState, partialTick);
		model.handleAnimations(createAnimationState(renderState));
		poseStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
		defaultRender(renderState, poseStack, bufferSource, null, null);
	}

	@Override
	public long getInstanceId(MegumiWingsModel.Animatable animatable, AbstractClientPlayer player) {
		return player.getUUID().getLeastSignificantBits();
	}

	@Override
	public void fireCompileRenderLayersEvent() {}

	@Override
	public void fireCompileRenderStateEvent(MegumiWingsModel.Animatable animatable,
			AbstractClientPlayer player, GeoRenderState renderState) {}

	@Override
	public boolean firePreRenderEvent(GeoRenderState renderState, PoseStack poseStack,
			BakedGeoModel model, MultiBufferSource bufferSource) {
		return true;
	}

	@Override
	public void firePostRenderEvent(GeoRenderState renderState, PoseStack poseStack,
			BakedGeoModel model, MultiBufferSource bufferSource) {}
}
