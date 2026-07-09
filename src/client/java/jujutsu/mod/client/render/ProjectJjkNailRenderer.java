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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEmbedding;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNailEntity;
import jujutsu.mod.registry.JujutsuItems;

public final class ProjectJjkNailRenderer extends EntityRenderer<ProjectJjkNailEntity, ProjectJjkNailRenderer.State> {
	private static final Vector3f MODEL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
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
		state.embeddedAnchorOffset = Vec3.ZERO;
		state.hasEmbeddedAnchor = false;
		if (state.embedded) {
			Entity host = entity.embeddedTargetEntityId() < 0 ? null : entity.level().getEntity(entity.embeddedTargetEntityId());
			if (host instanceof LivingEntity living && living.isAlive()) {
				Vec3 hostPosition = living.getPosition(partialTick);
				float bodyYaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
				Vec3 anchor = hostPosition.add(ProjectJjkNailEmbedding.worldOffset(entity.embeddedLocalOffset(), bodyYaw));
				state.embeddedAnchorOffset = anchor.subtract(state.x, state.y, state.z);
				state.hasEmbeddedAnchor = true;
				state.direction = safeDirection(ProjectJjkNailEmbedding.worldForward(entity.embeddedLocalForward(), bodyYaw));
			}
		}
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		Vec3 direction = safeDirection(state.direction);
		matrices.pushPose();
		if (state.embedded && state.hasEmbeddedAnchor) {
			matrices.translate(state.embeddedAnchorOffset.x, state.embeddedAnchorOffset.y, state.embeddedAnchorOffset.z);
		}
		matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(direction)));
		if (state.embedded) {
			matrices.translate(0.0f, -0.18f, 0.0f);
		}
		matrices.pushPose();
		float scale = state.embedded ? 0.58f : state.launched ? 0.7f : 0.62f;
		matrices.mulPose(new Quaternionf().rotateY((float) ((state.seed & 3) * Math.PI * 0.5)));
		matrices.scale(scale, scale, scale);
		itemRenderer.renderStatic(
				NAIL_STACK,
				ItemDisplayContext.FIXED,
				packedLight,
				OverlayTexture.NO_OVERLAY,
				matrices,
				consumers,
				Minecraft.getInstance().level,
				state.seed
		);
		matrices.popPose();
		if (!state.embedded) {
			renderCyanNailFireAura(consumers.getBuffer(RenderType.lightning()), matrices, direction, state.age, state.launched ? 0.92f : 0.62f);
		}
		matrices.popPose();
		super.render(state, matrices, consumers, packedLight);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static Vector3f toVector3f(Vec3 vector) {
		return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
	}

	private static void renderCyanNailFireAura(VertexConsumer consumer, PoseStack matrices, Vec3 direction, float age, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 line = safeDirection(direction);
		Vec3 side = line.cross(UP);
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(EAST);
		}
		side = side.normalize();
		Vec3 cross = line.cross(side).normalize();
		Vec3 center = Vec3.ZERO;
		for (int tongue = 0; tongue < 7; tongue++) {
			double phase = age * 0.42 + tongue * 0.92;
			double angle = phase + tongue * Math.PI * 2.0 / 7.0;
			Vec3 radial = side.scale(Math.cos(angle)).add(cross.scale(Math.sin(angle))).normalize();
			Vec3 tangent = side.scale(-Math.sin(angle)).add(cross.scale(Math.cos(angle))).normalize();
			double flicker = 0.72 + 0.28 * Math.sin(age * 0.67 + tongue * 1.31);
			Vec3 start = center.subtract(line.scale(0.38)).add(radial.scale(0.095 * flicker));
			Vec3 end = center.add(line.scale(0.46 + 0.06 * flicker)).add(radial.scale(0.26 + 0.05 * flicker)).add(tangent.scale(0.035 * Math.sin(phase)));
			addRibbon(consumer, matrices, start, end, tangent.scale(0.026 + tongue % 2 * 0.006), 0, 80, 130, Math.round(92.0f * alpha));
			addRibbon(consumer, matrices, start.lerp(end, 0.18), end, tangent.scale(0.012), 70, 238, 255, Math.round(150.0f * alpha));
		}
		addRibbon(consumer, matrices, center.subtract(line.scale(0.42)), center.add(line.scale(0.42)), side.scale(0.045), 180, 255, 255, Math.round(70.0f * alpha));
		addRibbon(consumer, matrices, center.subtract(line.scale(0.36)), center.add(line.scale(0.44)), cross.scale(0.035), 16, 190, 255, Math.round(125.0f * alpha));
	}

	private static void addRibbon(VertexConsumer consumer, PoseStack matrices, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		PoseStack.Pose pose = matrices.last();
		consumer.addVertex(pose, (float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex(pose, (float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
	}

	public static final class State extends EntityRenderState {
		private Vec3 direction = new Vec3(0.0, 0.0, 1.0);
		private boolean launched;
		private boolean embedded;
		private int seed;
		private float age;
		private boolean hasEmbeddedAnchor;
		private Vec3 embeddedAnchorOffset = Vec3.ZERO;
	}
}
