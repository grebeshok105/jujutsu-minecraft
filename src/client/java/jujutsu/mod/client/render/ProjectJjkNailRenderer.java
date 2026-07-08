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

	private static void renderEmbeddedMark(PoseStack matrices, MultiBufferSource consumers, State state) {
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrices.last();
		float pulse = 0.82f + 0.18f * (float) Math.sin(state.age * 0.18f);
		int alpha = Math.round(84.0f * pulse);
		addRibbon(consumer, pose, -0.28f, -0.06f, 0.0f, 0.28f, -0.06f, 0.0f, 0.0f, 0.0f, 0.052f, 24, 3, 8, alpha);
		addRibbon(consumer, pose, 0.0f, -0.09f, -0.24f, 0.0f, -0.09f, 0.24f, 0.052f, 0.0f, 0.0f, 44, 4, 12, alpha / 2);
		addRibbon(consumer, pose, -0.14f, -0.02f, -0.14f, 0.14f, -0.02f, 0.14f, 0.026f, 0.0f, -0.026f, 74, 8, 16, alpha / 3);
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
