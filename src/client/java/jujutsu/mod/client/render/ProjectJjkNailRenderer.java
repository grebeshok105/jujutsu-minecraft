package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;

public final class ProjectJjkNailRenderer extends EntityRenderer<ProjectJjkNailEntity, ProjectJjkNailRenderer.State> {
	private static final Vector3f MODEL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final ResourceLocation NAIL_TEXTURE = JujutsuMod.id("textures/projectjjk/entity/nail.png");
	private static final int BLUE_DARK_R = 7;
	private static final int BLUE_DARK_G = 24;
	private static final int BLUE_DARK_B = 96;
	private static final int BLUE_R = 32;
	private static final int BLUE_G = 104;
	private static final int BLUE_B = 218;
	private static final int BLUE_EDGE_R = 110;
	private static final int BLUE_EDGE_G = 202;
	private static final int BLUE_EDGE_B = 255;
	private static final int BLUE_WHITE_R = 176;
	private static final int BLUE_WHITE_G = 230;
	private static final int BLUE_WHITE_B = 255;

	public ProjectJjkNailRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(ProjectJjkNailEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.direction = safeDirection(entity.forwardDirection());
		state.launched = entity.isFlying();
		state.embedded = entity.isEmbedded();
		state.seed = entity.getId();
		state.age = entity.tickCount + partialTick;
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		Vec3 direction = safeDirection(state.direction);
		matrices.pushPose();
		matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(direction)));
		if (!state.embedded) {
			renderCursedAura(matrices, consumers, state);
		}
		renderNailGeometry(matrices, consumers, packedLight, state);
		matrices.popPose();
		super.render(state, matrices, consumers, packedLight);
	}

	private static void renderNailGeometry(PoseStack matrices, MultiBufferSource consumers, int packedLight, State state) {
		VertexConsumer consumer = consumers.getBuffer(RenderType.entityCutoutNoCull(NAIL_TEXTURE));
		PoseStack.Pose pose = matrices.last();
		float scale = state.launched ? 0.82f : 0.74f;
		if (state.embedded) {
			scale = 0.68f;
		}
		renderBox(consumer, pose, -0.032f * scale, -0.30f * scale, -0.032f * scale, 0.032f * scale, 0.28f * scale, 0.032f * scale, packedLight, 216, 208, 188);
		renderBox(consumer, pose, -0.088f * scale, -0.43f * scale, -0.088f * scale, 0.088f * scale, -0.29f * scale, 0.088f * scale, packedLight, 146, 130, 106);
		renderBox(consumer, pose, -0.046f * scale, 0.27f * scale, -0.046f * scale, 0.046f * scale, 0.41f * scale, 0.046f * scale, packedLight, 188, 178, 158);
	}

	private static void renderCursedAura(PoseStack matrices, MultiBufferSource consumers, State state) {
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrices.last();
		float alpha = state.launched ? 0.82f : 0.44f;
		float length = state.launched ? 2.62f : 1.54f;
		float radius = state.launched ? 0.62f : 0.34f;
		float tail = -length * 0.54f;
		float head = length * 0.48f;
		renderAuraShell(consumer, pose, state.age, alpha, tail, head, radius, state.launched ? 8 : 5);
		renderAuraRings(consumer, pose, state.age, alpha, tail, head, radius, state.launched ? 5 : 3);
		renderAuraCore(consumer, pose, alpha, tail, head, radius);
	}

	private static void renderAuraShell(VertexConsumer consumer, PoseStack.Pose pose, float age, float alpha, float tail, float head, float radius, int count) {
		for (int index = 0; index < count; index++) {
			float phase = age * 0.22f + index * 0.78f;
			float startAngle = (float) (phase + Math.PI * 2.0 * index / count);
			float endAngle = startAngle + (statefulTwist(age, index) * 0.72f);
			float startRadius = radius * (0.78f + 0.14f * (float) Math.sin(age * 0.34f + index));
			float endRadius = radius * (0.44f + 0.10f * (float) Math.cos(age * 0.41f + index * 1.7f));
			float sx = (float) Math.cos(startAngle) * startRadius;
			float sz = (float) Math.sin(startAngle) * startRadius;
			float ex = (float) Math.cos(endAngle) * endRadius;
			float ez = (float) Math.sin(endAngle) * endRadius;
			float sideX = (float) -Math.sin(startAngle) * radius * 0.16f;
			float sideZ = (float) Math.cos(startAngle) * radius * 0.16f;
			int alphaOuter = Math.round(76.0f * alpha);
			int alphaEdge = Math.round(112.0f * alpha);
			addRibbon(consumer, pose, sx, tail, sz, ex, head, ez, sideX, 0.0f, sideZ, BLUE_DARK_R, BLUE_DARK_G, BLUE_DARK_B, alphaOuter);
			addRibbon(consumer, pose, sx * 0.68f, tail * 0.82f, sz * 0.68f, ex * 0.74f, head * 0.94f, ez * 0.74f, sideX * 0.48f, 0.0f, sideZ * 0.48f, BLUE_EDGE_R, BLUE_EDGE_G, BLUE_EDGE_B, alphaEdge);
		}
	}

	private static void renderAuraRings(VertexConsumer consumer, PoseStack.Pose pose, float age, float alpha, float tail, float head, float radius, int rings) {
		for (int ring = 0; ring < rings; ring++) {
			float t = (ring + 0.5f) / rings;
			float y = tail + (head - tail) * t;
			float pulse = 0.84f + 0.16f * (float) Math.sin(age * 0.52f + ring * 1.37f);
			float ringRadius = radius * pulse * (1.06f - t * 0.32f);
			int alphaRing = Math.round((ring == rings - 1 ? 64.0f : 46.0f) * alpha);
			for (int segment = 0; segment < 8; segment++) {
				float a0 = (float) (Math.PI * 2.0 * segment / 8.0 + age * 0.018f);
				float a1 = (float) (Math.PI * 2.0 * (segment + 0.62f) / 8.0 + age * 0.018f);
				addRibbon(
						consumer,
						pose,
						(float) Math.cos(a0) * ringRadius,
						y,
						(float) Math.sin(a0) * ringRadius,
						(float) Math.cos(a1) * ringRadius,
						y,
						(float) Math.sin(a1) * ringRadius,
						0.0f,
						radius * 0.025f,
						0.0f,
						BLUE_R,
						BLUE_G,
						BLUE_B,
						alphaRing
				);
			}
		}
	}

	private static void renderAuraCore(VertexConsumer consumer, PoseStack.Pose pose, float alpha, float tail, float head, float radius) {
		addRibbon(consumer, pose, -radius * 0.16f, tail * 0.72f, 0.0f, -radius * 0.08f, head * 1.08f, 0.0f, radius * 0.16f, 0.0f, 0.0f, BLUE_WHITE_R, BLUE_WHITE_G, BLUE_WHITE_B, Math.round(38.0f * alpha));
		addRibbon(consumer, pose, 0.0f, tail * 0.58f, -radius * 0.14f, 0.0f, head, -radius * 0.08f, 0.0f, 0.0f, radius * 0.14f, BLUE_EDGE_R, BLUE_EDGE_G, BLUE_EDGE_B, Math.round(58.0f * alpha));
	}

	private static float statefulTwist(float age, int index) {
		return 1.0f + 0.24f * (float) Math.sin(age * 0.31f + index * 0.93f);
	}

	private static void renderBox(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int light, int red, int green, int blue) {
		addTexturedQuad(consumer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, light, red, green, blue, 0.0f, 0.25f, 0.5f, 0.75f, 0.0f, 0.0f, 1.0f);
		addTexturedQuad(consumer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, light, red, green, blue, 0.0f, 0.25f, 0.5f, 0.75f, 0.0f, 0.0f, -1.0f);
		addTexturedQuad(consumer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, light, red, green, blue, 0.0f, 0.25f, 0.5f, 0.75f, 1.0f, 0.0f, 0.0f);
		addTexturedQuad(consumer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, light, red, green, blue, 0.0f, 0.25f, 0.5f, 0.75f, -1.0f, 0.0f, 0.0f);
		addTexturedQuad(consumer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, light, red, green, blue, 0.5f, 0.0f, 1.0f, 0.25f, 0.0f, 1.0f, 0.0f);
		addTexturedQuad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, light, red, green, blue, 0.5f, 0.75f, 1.0f, 1.0f, 0.0f, -1.0f, 0.0f);
	}

	private static void addTexturedQuad(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, int light, int red, int green, int blue, float u0, float v0, float u1, float v1, float normalX, float normalY, float normalZ) {
		addTexturedVertex(consumer, pose, x0, y0, z0, light, red, green, blue, u0, v1, normalX, normalY, normalZ);
		addTexturedVertex(consumer, pose, x1, y1, z1, light, red, green, blue, u1, v1, normalX, normalY, normalZ);
		addTexturedVertex(consumer, pose, x2, y2, z2, light, red, green, blue, u1, v0, normalX, normalY, normalZ);
		addTexturedVertex(consumer, pose, x3, y3, z3, light, red, green, blue, u0, v0, normalX, normalY, normalZ);
	}

	private static void addTexturedVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, int light, int red, int green, int blue, float u, float v, float normalX, float normalY, float normalZ) {
		consumer.addVertex(pose, x, y, z)
				.setColor(red, green, blue, 255)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, normalX, normalY, normalZ);
	}

	private static void addRibbon(VertexConsumer consumer, PoseStack.Pose pose, float startX, float startY, float startZ, float endX, float endY, float endZ, float sideX, float sideY, float sideZ, int red, int green, int blue, int alpha) {
		consumer.addVertex(pose, startX - sideX, startY - sideY, startZ - sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX - sideX, endY - sideY, endZ - sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX + sideX, endY + sideY, endZ + sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, startX + sideX, startY + sideY, startZ + sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, startX + sideX, startY + sideY, startZ + sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX + sideX, endY + sideY, endZ + sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX - sideX, endY - sideY, endZ - sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, startX - sideX, startY - sideY, startZ - sideZ).setColor(red, green, blue, alpha);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static Vector3f toVector3f(Vec3 vector) {
		return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
	}

	public static final class State extends EntityRenderState {
		private Vec3 direction = new Vec3(0.0, 0.0, 1.0);
		private boolean launched;
		private boolean embedded;
		private int seed;
		private float age;
	}
}
