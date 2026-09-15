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
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * One renderer class, three instances (one per tier type). Per-variant model dispatch
 * follows the vanilla {@code TropicalFishRenderer} shape: select the rig, then
 * {@code super.render(...)}.
 */
public class CursedSpiritRenderer extends
		MobRenderer<CursedSpiritEntity, CursedSpiritRenderState, EntityModel<CursedSpiritRenderState>> {
	private final Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> rigs;

	public CursedSpiritRenderer(EntityRendererProvider.Context context, float shadowRadius) {
		this(context, shadowRadius, CursedSpiritModels.bakeAll(context));
	}

	private CursedSpiritRenderer(EntityRendererProvider.Context context, float shadowRadius,
			Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> rigs) {
		super(context, rigs.get(CursedSpiritVariant.PROWLER), shadowRadius);
		this.rigs = rigs;
	}

	@Override
	public void render(CursedSpiritRenderState state, PoseStack poses, MultiBufferSource buffers, int light) {
		// Issue #80: subjects render for perceivers only. The subject bit comes from the
		// entity via extractRenderState (isSubject, not instanceof), so future curse bodies
		// inherit the rule through their own renderers.
		if (!CurseRenderGate.shouldRender(state.curseSubject)) {
			return;
		}
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
		state.curseSubject = CursePerception.isSubject(entity);
		if (CurseRenderGate.hiddenFromView(state.curseSubject)) {
			// Issue #80 render leak: EntityRenderDispatcher draws the shadow and the
			// F3+B hitbox past our early return in render(). javap-verified 1.21.8:
			// both paths are gated on EntityRenderState.isInvisible (shadow also needs
			// getShadowRadius > 0), while the on-fire flame checks displayFireAnimation
			// only — suppress all three so a non-perceiver sees nothing.
			state.isInvisible = true;
			state.displayFireAnimation = false;
		}
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
