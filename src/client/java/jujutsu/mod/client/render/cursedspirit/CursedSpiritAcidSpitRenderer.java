package jujutsu.mod.client.render.cursedspirit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.cursedspirit.ability.effects.CursedSpiritAcidSpitEntity;
import jujutsu.mod.cursedspirit.perception.CursePerception;

/**
 * The flying acid glob: the stone renderer's cube geometry with a sickly green tint and a
 * faster, tighter spin, so the glob never reads as just another thrown rock. State-extract
 * API like the stone: the render pass never touches the entity.
 */
public final class CursedSpiritAcidSpitRenderer
		extends EntityRenderer<CursedSpiritAcidSpitEntity, CursedSpiritAcidSpitRenderer.State> {
	private static final ResourceLocation TEXTURE =
			JujutsuMod.id("textures/entity/cursed_acid_spit.png");
	private static final float HALF_EXTENT = 0.11f;
	private static final float SPIN_PER_TICK = 0.30f;

	public CursedSpiritAcidSpitRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(CursedSpiritAcidSpitEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.age = entity.tickCount + partialTick;
		state.seed = entity.getId();
		state.curseSubject = CursePerception.isSubject(entity);
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		// Issue #80: subjects render for perceivers only, like CursedSpiritRenderer.
		if (!CurseRenderGate.shouldRender(state.curseSubject)) {
			return;
		}
		float phase = (state.seed & 7) * 0.63f;
		float wobble = 0.55f + 0.45f * Mth.sin(state.age * 0.23f + phase);
		matrices.mulPose(new Quaternionf().rotateY(state.age * SPIN_PER_TICK + phase)
				.rotateX(wobble * 0.30f));
		VertexConsumer consumer = consumers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		int light = LightTexture.lightCoordsWithEmission(packedLight, 3);
		renderCube(consumer, matrices, HALF_EXTENT, light);
		super.render(state, matrices, consumers, packedLight);
	}

	/** Six textured quads in counter-clockwise winding, tinted acid green. */
	private static void renderCube(VertexConsumer consumer, PoseStack matrices, float h, int light) {
		PoseStack.Pose pose = matrices.last();
		quad(consumer, pose, h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1.0f, 0.0f, 0.0f, light);
		quad(consumer, pose, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1.0f, 0.0f, 0.0f, light);
		quad(consumer, pose, -h, h, -h, -h, h, h, h, h, h, h, h, -h, 0.0f, 1.0f, 0.0f, light);
		quad(consumer, pose, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h, 0.0f, -1.0f, 0.0f, light);
		quad(consumer, pose, -h, -h, h, h, -h, h, h, h, h, -h, h, h, 0.0f, 0.0f, 1.0f, light);
		quad(consumer, pose, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0.0f, 0.0f, -1.0f, light);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz, int light) {
		consumer.addVertex(pose, x0, y0, z0).setUv(0.0f, 0.0f).setColor(130, 255, 130, 255)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x1, y1, z1).setUv(1.0f, 0.0f).setColor(130, 255, 130, 255)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x2, y2, z2).setUv(1.0f, 1.0f).setColor(130, 255, 130, 255)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x3, y3, z3).setUv(0.0f, 1.0f).setColor(130, 255, 130, 255)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
	}

	public static final class State extends EntityRenderState {
		private float age;
		private int seed;
		/** Issue #80: whether the source glob is a curse subject (for {@code CurseRenderGate}). */
		public boolean curseSubject = true;
	}
}
