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
	private static final ItemStack NAIL_STACK = new ItemStack(JujutsuItems.PROJECTJJK_HAIRPIN_NAIL);
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
		state.direction = safeDirection(entity.getViewVector(partialTick));
		state.launched = entity.isFlying();
		state.seed = entity.getId();
		state.age = entity.tickCount + partialTick;
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		Vec3 direction = safeDirection(state.direction);
		matrices.pushPose();
		matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(direction)));
		renderBlueFlame(matrices, consumers, state);
		matrices.pushPose();
		float scale = state.launched ? 0.7f : 0.62f;
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

	private static void renderBlueFlame(PoseStack matrices, MultiBufferSource consumers, State state) {
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		PoseStack.Pose pose = matrices.last();
		float alpha = state.launched ? 1.0f : 0.72f;
		float length = state.launched ? 1.72f : 1.18f;
		float width = state.launched ? 0.34f : 0.23f;
		float tail = -length * 0.52f;
		float head = length * 0.52f;
		addRibbon(consumer, pose, -width * 1.55f, tail, 0.0f, -width * 1.0f, head, 0.0f, width * 1.55f, 0.0f, 0.0f, BLUE_DARK_R, BLUE_DARK_G, BLUE_DARK_B, Math.round(150.0f * alpha));
		addRibbon(consumer, pose, 0.0f, tail, -width * 1.18f, 0.0f, head, -width * 0.78f, 0.0f, 0.0f, width * 1.18f, BLUE_R, BLUE_G, BLUE_B, Math.round(188.0f * alpha));
		addRibbon(consumer, pose, -width * 0.38f, tail * 0.72f, 0.0f, -width * 0.24f, head * 1.06f, 0.0f, width * 0.38f, 0.0f, 0.0f, BLUE_EDGE_R, BLUE_EDGE_G, BLUE_EDGE_B, Math.round(122.0f * alpha));
		addRibbon(consumer, pose, 0.0f, tail * 0.54f, -width * 0.25f, 0.0f, head * 0.94f, -width * 0.18f, 0.0f, 0.0f, width * 0.25f, BLUE_WHITE_R, BLUE_WHITE_G, BLUE_WHITE_B, Math.round(46.0f * alpha));
		renderTongues(consumer, pose, state.age, alpha, length, width, state.launched ? 7 : 4);
	}

	private static void renderTongues(VertexConsumer consumer, PoseStack.Pose pose, float age, float alpha, float length, float width, int count) {
		float tail = -length * 0.48f;
		for (int index = 0; index < count; index++) {
			float offset = (index + 0.7f) / (count + 0.8f);
			float y = tail + length * offset;
			float wave = (float) (Math.sin(age * 0.68f + index * 1.73f) * 0.5f + 0.5f);
			float reach = width * (1.18f + wave * 0.95f);
			float side = width * (0.08f + wave * 0.05f);
			if ((index & 1) == 0) {
				float sign = index % 4 == 0 ? 1.0f : -1.0f;
				addRibbon(consumer, pose, 0.0f, y, 0.0f, sign * reach, y - length * 0.12f, width * 0.16f, 0.0f, 0.0f, side, BLUE_EDGE_R, BLUE_EDGE_G, BLUE_EDGE_B, Math.round(116.0f * alpha));
				addRibbon(consumer, pose, sign * reach * 0.28f, y - length * 0.03f, width * 0.04f, sign * reach, y - length * 0.12f, width * 0.16f, 0.0f, 0.0f, side * 0.36f, BLUE_WHITE_R, BLUE_WHITE_G, BLUE_WHITE_B, Math.round(32.0f * alpha));
			} else {
				float sign = index % 3 == 0 ? 1.0f : -1.0f;
				addRibbon(consumer, pose, 0.0f, y, 0.0f, width * 0.16f, y - length * 0.12f, sign * reach, side, 0.0f, 0.0f, BLUE_EDGE_R, BLUE_EDGE_G, BLUE_EDGE_B, Math.round(116.0f * alpha));
				addRibbon(consumer, pose, width * 0.04f, y - length * 0.03f, sign * reach * 0.28f, width * 0.16f, y - length * 0.12f, sign * reach, side * 0.36f, 0.0f, 0.0f, BLUE_WHITE_R, BLUE_WHITE_G, BLUE_WHITE_B, Math.round(32.0f * alpha));
			}
		}
	}

	private static void addRibbon(VertexConsumer consumer, PoseStack.Pose pose, float startX, float startY, float startZ, float endX, float endY, float endZ, float sideX, float sideY, float sideZ, int red, int green, int blue, int alpha) {
		consumer.addVertex(pose, startX - sideX, startY - sideY, startZ - sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX - sideX, endY - sideY, endZ - sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, endX + sideX, endY + sideY, endZ + sideZ).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, startX + sideX, startY + sideY, startZ + sideZ).setColor(red, green, blue, alpha);
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
		private int seed;
		private float age;
	}
}
