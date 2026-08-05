package jujutsu.mod.client.render.todo;

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
import jujutsu.mod.character.todo.TodoStoneEntity;

/**
 * The flying stone: a small tumbling pebble drawn from code geometry against its own texture.
 *
 * <p>Everything the renderer needs is extracted into {@link State} on the client thread and read
 * back in {@link #render} (1.21.8 state-extract API), so the render pass never touches the entity.
 * The tumble is driven purely by age plus an entity-derived seed, and the last ticks of life fade
 * the stone out ahead of the vanish cue so the end reads as a poof, not a pop.
 */
public final class TodoStoneRenderer extends EntityRenderer<TodoStoneEntity, TodoStoneRenderer.State> {
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/todo_stone.png");
	/**
	 * Half extent of the cube, deliberately below {@code STONE_HITBOX_SIZE}: a collision that reads
	 * as generous is fairer than an invisible one.
	 */
	private static final float HALF_EXTENT = 0.09f;
	/** The stone fades out over its last ten ticks of flight, matching the vanish poof. */
	private static final int FADE_TICKS = 10;
	private static final float TUMBLE_SPIN_PER_TICK = 0.16f;

	public TodoStoneRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.0f;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TodoStoneEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.age = entity.tickCount + partialTick;
		state.seed = entity.getId();
		state.fadeAlpha = Math.min(1.0f, entity.remainingTicks() / (float) FADE_TICKS);
	}

	@Override
	public void render(State state, PoseStack matrices, MultiBufferSource consumers, int packedLight) {
		if (state.fadeAlpha <= 0.01f) {
			return;
		}
		// A slow spin around Y with a gentle wobble reads as tumbling without ever flipping end
		// over end; the seed keeps two stones in the same world from tumbling in lockstep.
		float phase = (state.seed & 7) * 0.63f;
		float wobble = 0.55f + 0.45f * Mth.sin(state.age * 0.11f + phase);
		matrices.mulPose(new Quaternionf().rotateY(state.age * TUMBLE_SPIN_PER_TICK + phase)
				.rotateX(wobble * 0.30f));
		VertexConsumer consumer = consumers.getBuffer(RenderType.entityTranslucent(TEXTURE));
		// A small emission keeps the stone readable at the edge of the swap range at night.
		int light = LightTexture.lightCoordsWithEmission(packedLight, 2);
		int alpha = Math.round(255.0f * state.fadeAlpha);
		renderCube(consumer, matrices, HALF_EXTENT, light, alpha);
		super.render(state, matrices, consumers, packedLight);
	}

	/** Six textured quads in counter-clockwise winding (front faces cull correctly). */
	private static void renderCube(VertexConsumer consumer, PoseStack matrices, float h, int light, int alpha) {
		PoseStack.Pose pose = matrices.last();
		quad(consumer, pose, h, -h, h, h, -h, -h, h, h, -h, h, h, h, 1.0f, 0.0f, 0.0f, light, alpha);
		quad(consumer, pose, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h, -1.0f, 0.0f, 0.0f, light, alpha);
		quad(consumer, pose, -h, h, -h, -h, h, h, h, h, h, h, h, -h, 0.0f, 1.0f, 0.0f, light, alpha);
		quad(consumer, pose, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h, 0.0f, -1.0f, 0.0f, light, alpha);
		quad(consumer, pose, -h, -h, h, h, -h, h, h, h, h, -h, h, h, 0.0f, 0.0f, 1.0f, light, alpha);
		quad(consumer, pose, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h, 0.0f, 0.0f, -1.0f, light, alpha);
	}

	private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
			float x0, float y0, float z0, float x1, float y1, float z1,
			float x2, float y2, float z2, float x3, float y3, float z3,
			float nx, float ny, float nz, int light, int alpha) {
		consumer.addVertex(pose, x0, y0, z0).setUv(0.0f, 0.0f).setColor(255, 255, 255, alpha)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x1, y1, z1).setUv(1.0f, 0.0f).setColor(255, 255, 255, alpha)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x2, y2, z2).setUv(1.0f, 1.0f).setColor(255, 255, 255, alpha)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
		consumer.addVertex(pose, x3, y3, z3).setUv(0.0f, 1.0f).setColor(255, 255, 255, alpha)
				.setLight(light).setOverlay(OverlayTexture.NO_OVERLAY).setNormal(pose, nx, ny, nz);
	}

	public static final class State extends EntityRenderState {
		private float age;
		private int seed;
		private float fadeAlpha = 1.0f;
	}
}
