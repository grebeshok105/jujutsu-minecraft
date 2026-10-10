package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import jujutsu.mod.character.megumi.MegumiOxEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** GeckoLib renderer for the Piercing Ox. */
public final class MegumiOxRenderer extends
		GeoReplacedEntityRenderer<MegumiOxGeoAnimatable, MegumiOxEntity, MegumiShikigamiRenderState> {
	/** Uniform render scale — verified against the entity hitbox in the live lane. */
	private static final float MODEL_SCALE = 1.0f;

	public MegumiOxRenderer(EntityRendererProvider.Context context) {
		super(context, new MegumiOxModel(), MegumiOxGeoAnimatable.INSTANCE);
		withScale(MODEL_SCALE);
	}

	@Override
	protected MegumiShikigamiRenderState createBaseRenderState(MegumiOxEntity entity) {
		return new MegumiShikigamiRenderState();
	}

	@Override
	public void extractRenderState(MegumiOxEntity entity, MegumiShikigamiRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = entity.phase();
		state.progress = MegumiShikigamiPresentationPolicy.progress(state.phase, entity.phaseTicks(),
				partialTick, entity.materializeTicks(), entity.recallTicks());
		state.verticalOffset = MegumiShikigamiPresentationPolicy.verticalOffset(state.phase, state.progress);
		state.actionActive = entity.actionTicks() > 0;
		state.actionIndex = entity.presentationAction();
	}

	@Override
	public void preRender(MegumiShikigamiRenderState renderState, PoseStack poseStack, BakedGeoModel model,
			@Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
			int packedLight, int packedOverlay, int renderColor) {
		poseStack.translate(0.0f, renderState.verticalOffset, 0.0f);
		super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
	}
}
