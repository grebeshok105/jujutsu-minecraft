package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.animal.wolf.WolfVariant;
import net.minecraft.world.entity.animal.wolf.WolfVariants;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** GeckoLib Divine Dog renderer replacing the vanilla wolf visual with the Dire Wolf model. */
public final class MegumiDivineDogRenderer extends
		GeoReplacedEntityRenderer<MegumiDogGeoAnimatable, MegumiDivineDogEntity, MegumiDivineDogRenderState> {
	/** Dire Wolf model is larger than the vanilla wolf: 41.8 units tall vs a 0.85 hitbox. Tune in-game. */
	private static final float MODEL_SCALE = 0.5f;

	public MegumiDivineDogRenderer(EntityRendererProvider.Context context) {
		super(context, new MegumiDivineDogModel(), MegumiDogGeoAnimatable.INSTANCE);
		withScale(MODEL_SCALE);
	}

	@Override
	protected MegumiDivineDogRenderState createBaseRenderState(MegumiDivineDogEntity entity) {
		return new MegumiDivineDogRenderState();
	}

	@Override
	public void extractRenderState(MegumiDivineDogEntity entity, MegumiDivineDogRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.phase = entity.presentationPhase();
		state.progress = MegumiDogPresentationPolicy.progress(state.phase, entity.presentationTicks(), partialTick);
		state.verticalOffset = MegumiDogPresentationPolicy.verticalOffset(state.phase, state.progress);
		Holder<WolfVariant> variant = entity.get(DataComponents.WOLF_VARIANT);
		state.blackVariant = variant != null && variant.is(WolfVariants.BLACK);
		state.attackAnim = entity.getAttackAnim(partialTick);
	}

	/**
	 * Applies the presentation offset, then lets GeckoLib capture its entity anchor for bone-attached
	 * rendering. The order matters: capturing after the translation makes {@code entityRenderTranslations}
	 * describe the rendered position, so a future bone-matrix consumer (glow layer, bone-attached geometry)
	 * resolves against the offset silhouette rather than the bare entity origin.
	 */
	@Override
	public void preRender(MegumiDivineDogRenderState renderState, PoseStack poseStack, BakedGeoModel model,
			@Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender,
			int packedLight, int packedOverlay, int renderColor) {
		poseStack.translate(0.0f, renderState.verticalOffset, 0.0f);
		super.preRender(renderState, poseStack, model, bufferSource, buffer, isReRender, packedLight, packedOverlay, renderColor);
	}
}
