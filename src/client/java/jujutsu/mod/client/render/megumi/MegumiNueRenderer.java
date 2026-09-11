package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import jujutsu.mod.character.megumi.MegumiNueEntity;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** GeckoLib renderer for Nue: the imported model replaces the placeholder body entirely. */
public final class MegumiNueRenderer extends
		GeoReplacedEntityRenderer<MegumiNueGeoAnimatable, MegumiNueEntity, MegumiShikigamiRenderState> {
	/** Imported model is authored for a larger silhouette than the 0.9 hitbox; tuned in game. */
	private static final float MODEL_SCALE = 0.55f;

	public MegumiNueRenderer(EntityRendererProvider.Context context) {
		super(context, new MegumiNueModel(), MegumiNueGeoAnimatable.INSTANCE);
		withScale(MODEL_SCALE);
	}

	@Override
	protected MegumiShikigamiRenderState createBaseRenderState(MegumiNueEntity entity) {
		return new MegumiShikigamiRenderState();
	}

	@Override
	public void extractRenderState(MegumiNueEntity entity, MegumiShikigamiRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = entity.phase();
		state.progress = MegumiShikigamiPresentationPolicy.progress(state.phase, entity.phaseTicks(),
				partialTick, entity.materializeTicks(), entity.recallTicks());
		state.verticalOffset = MegumiShikigamiPresentationPolicy.verticalOffset(state.phase, state.progress);
		state.actionActive = entity.actionTicks() > 0;
		state.attackAnim = entity.getAttackAnim(partialTick);
	}

	/** Same offset contract as the dog renderer: shift first, then let GeckoLib capture the anchor. */
	@Override
	public void preRender(MegumiShikigamiRenderState renderState, PoseStack poseStack, BakedGeoModel model,
			@Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
			int packedLight, int packedOverlay, int renderColor) {
		poseStack.translate(0.0f, renderState.verticalOffset, 0.0f);
		super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
	}
}
