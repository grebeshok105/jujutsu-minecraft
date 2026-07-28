package jujutsu.mod.client.render.megumi;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.world.entity.animal.wolf.Wolf;
import jujutsu.mod.character.megumi.MegumiDivineDogEntity;
import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** Vanilla Divine Dog renderer seam for Megumi-specific presentation. */
public final class MegumiDivineDogRenderer extends WolfRenderer {
	public MegumiDivineDogRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public MegumiDivineDogRenderState createRenderState() {
		return new MegumiDivineDogRenderState();
	}

	@Override
	public void extractRenderState(Wolf entity, WolfRenderState state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		if (entity instanceof MegumiDivineDogEntity dog && state instanceof MegumiDivineDogRenderState dogState) {
			dogState.phase = dog.presentationPhase();
			dogState.progress = MegumiDogPresentationPolicy.progress(
					dogState.phase, dog.presentationTicks(), partialTick);
			dogState.verticalOffset = MegumiDogPresentationPolicy.verticalOffset(
					dogState.phase, dogState.progress);
		}
	}

	@Override
	public void render(WolfRenderState state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		matrices.pushPose();
		try {
			if (state instanceof MegumiDivineDogRenderState dogState) {
				matrices.translate(0.0f, dogState.verticalOffset, 0.0f);
			}
			super.render(state, matrices, consumers, packedLight);
		} finally {
			matrices.popPose();
		}
	}
}
