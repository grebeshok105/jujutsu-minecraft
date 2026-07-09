package jujutsu.mod.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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
		matrices.popPose();
		super.render(state, matrices, consumers, packedLight);
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
		private boolean hasEmbeddedAnchor;
		private Vec3 embeddedAnchorOffset = Vec3.ZERO;
	}
}
