package jujutsu.mod.client.render.cursedspirit;

import java.util.Map;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.cursedspirit.CursedSpiritEntity;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * One renderer class, three instances (one per tier type). Per-variant model dispatch
 * follows the vanilla {@code TropicalFishRenderer} shape: select the rig, then
 * {@code super.render(...)}.
 */
public class CursedSpiritRenderer extends
		MobRenderer<CursedSpiritEntity, CursedSpiritRenderState, EntityModel<CursedSpiritRenderState>> {
	private final Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> rigs;

	public CursedSpiritRenderer(EntityRendererProvider.Context context, float shadowRadius) {
		super(context, CursedSpiritModels.bakeAll(context).get(CursedSpiritVariant.PROWLER), shadowRadius);
		this.rigs = CursedSpiritModels.bakeAll(context);
	}

	@Override
	public void render(CursedSpiritRenderState state, PoseStack poses, MultiBufferSource buffers, int light) {
		EntityModel<CursedSpiritRenderState> rig = this.rigs.get(state.variant);
		if (rig != null) {
			this.model = rig;
		}
		super.render(state, poses, buffers, light);
	}

	@Override
	public CursedSpiritRenderState createRenderState() {
		return new CursedSpiritRenderState();
	}

	@Override
	public void extractRenderState(CursedSpiritEntity entity, CursedSpiritRenderState state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		state.variant = entity.variant();
		state.idle.copyFrom(entity.idleAnimationState);
		state.attack.copyFrom(entity.attackAnimationState);
		state.scream.copyFrom(entity.screamAnimationState);
	}

	@Override
	public ResourceLocation getTextureLocation(CursedSpiritRenderState state) {
		return JujutsuMod.id(state.variant.texture());
	}

	@Override
	protected void scale(CursedSpiritRenderState state, PoseStack poses) {
		float scale = state.variant.renderScale();
		poses.scale(scale, scale, scale);
		poses.translate(0.0f, state.variant.renderOffsetY(), 0.0f);
	}
}
