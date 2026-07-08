package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.registry.JujutsuItems;

public final class ProjectJjkNailRenderer extends EntityRenderer<ProjectJjkNailEntity, ProjectJjkNailRenderer.State> {
	private static final Vector3f MODEL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final ItemStack NAIL_STACK = new ItemStack(JujutsuItems.HAIRPIN_NAIL);
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
	private final ItemRenderer itemRenderer;

	public ProjectJjkNailRenderer(EntityRendererProvider.Context context) {
		super(context);
		itemRenderer = Minecraft.getInstance().getItemRenderer();
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
		if (state.embedded) {
			renderEmbeddedMark(matrices, consumers, state);
		} else {
			renderCursedAura(matrices, consumers, state);
		}
		matrices.pushPose();
		float scale = state.embedded ? 0.54f : state.launched ? 0.7f : 0.62f;
		matrices.mulPose(new Quaternionf().rotateY((float) ((state.seed & 3) * Math.PI * 0.5)));
		matrices.scale(scale, scale, scale);
		itemRenderer.renderStatic(
				NAIL_STACK,
				ItemDisplayContext.NONE,
				packedLight,
				OverlayTexture.NO_OVERLAY,
				matrices,
				consumers,
				Minecraft.getInstance().level,
				state.seed
		);
		matrices.popPose();
		matrices.popPose();
		super.render(state, matrices, consumers, packedLight);
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

	private static void renderEmbeddedMark(PoseStack matrices, MultiBufferSource consumers, State state) {
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrices.last();
		float pulse = 0.82f + 0.18f * (float) Math.sin(state.age * 0.18f);
		int alpha = Math.round(84.0f * pulse);
		addRibbon(consumer, pose, -0.28f, -0.06f, 0.0f, 0.28f, -0.06f, 0.0f, 0.0f, 0.0f, 0.052f, 24, 3, 8, alpha);
		addRibbon(consumer, pose, 0.0f, -0.09f, -0.24f, 0.0f, -0.09f, 0.24f, 0.052f, 0.0f, 0.0f, 44, 4, 12, alpha / 2);
		addRibbon(consumer, pose, -0.14f, -0.02f, -0.14f, 0.14f, -0.02f, 0.14f, 0.026f, 0.0f, -0.026f, 74, 8, 16, alpha / 3);
	}

	private static float statefulTwist(float age, int index) {
		return 1.0f + 0.24f * (float) Math.sin(age * 0.31f + index * 0.93f);
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
