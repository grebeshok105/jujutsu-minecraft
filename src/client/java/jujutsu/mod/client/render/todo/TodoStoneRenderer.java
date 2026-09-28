package jujutsu.mod.client.render.todo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.character.todo.TodoStoneEntity;

/** GeckoLib pebble renderer with deterministic tumble, fade and a short motion ribbon. */
public final class TodoStoneRenderer extends GeoEntityRenderer<TodoStoneEntity, TodoStoneRenderer.State> {
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/todo_stone.png");
	private static final RenderType TRAIL_RENDER_TYPE = RenderType.entityTranslucent(TEXTURE);
	private static final int TRAIL_POINT_COUNT = 10;
	private static final int FADE_TICKS = 10;
	private static final float TUMBLE_SPIN_PER_TICK = 0.16f;
	private static final float TRAIL_WIDTH = 0.055f;

	private final Map<UUID, TrailBuffer> trailBuffers = new HashMap<>();

	public TodoStoneRenderer(EntityRendererProvider.Context context) {
		super(context, new TodoStoneModel());
		shadowRadius = 0.0f;
	}

	@Override
	protected State createBaseRenderState(TodoStoneEntity entity) {
		return new State();
	}

	@Override
	public void extractRenderState(TodoStoneEntity entity, State state, float partialTick) {
		super.extractRenderState(entity, state, partialTick);
		state.age = entity.tickCount + partialTick;
		state.seed = entity.getId();
		state.fadeAlpha = fadeAlpha(entity);
		state.center = entity.position();
		state.trail = trailSnapshot(entity);
	}
	@Override
	public void addRenderData(TodoStoneEntity animatable, Void relatedObject, State renderState) {
		Integer packedLight = renderState.getGeckolibData(DataTickets.PACKED_LIGHT);
		int light = packedLight == null ? LightTexture.FULL_BRIGHT : packedLight;
		renderState.addGeckolibData(DataTickets.PACKED_LIGHT,
				LightTexture.lightCoordsWithEmission(light, 2));
	}

	@Override
	public void render(State state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		if (state.fadeAlpha <= 0.01f) {
			return;
		}
		// The trail is world-space geometry relative to the entity anchor; tumble only the pebble.
		renderRibbon(state, poseStack, bufferSource, packedLight);
		float phase = (state.seed & 7) * 0.63f;
		float wobble = 0.55f + 0.45f * (float) Math.sin(state.age * 0.11f + phase);
		poseStack.mulPose(new Quaternionf().rotateY(state.age * TUMBLE_SPIN_PER_TICK + phase)
				.rotateX(wobble * 0.30f));
		super.render(state, poseStack, bufferSource, packedLight);
	}

	@Override
	public RenderType getRenderType(State renderState, ResourceLocation texture) {
		return RenderType.entityTranslucent(texture);
	}

	@Override
	public int getRenderColor(TodoStoneEntity entity, Void relatedObject, float partialTick) {
		int alpha = Math.round(255.0f * fadeAlpha(entity));
		return (alpha << 24) | 0x00FFFFFF;
	}

	private static float fadeAlpha(TodoStoneEntity entity) {
		return Math.min(1.0f, Math.max(0.0f, entity.remainingTicks() / (float) FADE_TICKS));
	}

	private List<Vec3> trailSnapshot(TodoStoneEntity entity) {
		UUID uuid = entity.getUUID();
		if (entity.isRemoved() || entity.remainingTicks() <= 0) {
			trailBuffers.remove(uuid);
			return List.of();
		}
		TrailBuffer buffer = trailBuffers.computeIfAbsent(uuid, ignored -> new TrailBuffer());
		Vec3 current = entity.position();
		if (buffer.lastTick != entity.tickCount) {
			if (buffer.lastPosition != null && buffer.lastPosition.distanceToSqr(current) > 16.0) {
				// A stone snap is a deliberate teleport; never draw a ribbon across the whole arena.
				buffer.positions.clear();
			}
			buffer.positions.addLast(current);
			while (buffer.positions.size() > TRAIL_POINT_COUNT) {
				buffer.positions.removeFirst();
			}
			buffer.lastPosition = current;
			buffer.lastTick = entity.tickCount;
		}
		return List.copyOf(buffer.positions);
	}

	private static void renderRibbon(State state, PoseStack poseStack, MultiBufferSource bufferSource,
			int packedLight) {
		if (state.trail.size() < 2) {
			return;
		}
		VertexConsumer consumer = bufferSource.getBuffer(TRAIL_RENDER_TYPE);
		PoseStack.Pose pose = poseStack.last();
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
		int light = LightTexture.lightCoordsWithEmission(packedLight, 2);
		int last = state.trail.size() - 1;
		for (int index = 0; index < last; index++) {
			Vec3 firstWorld = state.trail.get(index);
			Vec3 secondWorld = state.trail.get(index + 1);
			Vec3 segment = secondWorld.subtract(firstWorld);
			if (segment.lengthSqr() <= 1.0E-7) {
				continue;
			}
			float progress = (index + 1.0f) / last;
			Vec3 side = ribbonSide(segment, camera.subtract(firstWorld))
					.scale(TRAIL_WIDTH * (0.65 + 0.35 * progress));
			Vec3 first = firstWorld.subtract(state.center);
			Vec3 second = secondWorld.subtract(state.center);
			int alpha = Math.round(255.0f * state.fadeAlpha * (0.15f + 0.70f * progress));
			addRibbonVertex(consumer, pose, first.subtract(side), 0.0f, alpha, light);
			addRibbonVertex(consumer, pose, first.add(side), 1.0f, alpha, light);
			addRibbonVertex(consumer, pose, second.add(side), 1.0f, alpha, light);
			addRibbonVertex(consumer, pose, second.subtract(side), 0.0f, alpha, light);
		}
	}

	private static Vec3 ribbonSide(Vec3 segment, Vec3 toCamera) {
		Vec3 side = segment.cross(toCamera);
		if (side.lengthSqr() <= 1.0E-7) {
			side = segment.cross(new Vec3(0.0, 1.0, 0.0));
		}
		if (side.lengthSqr() <= 1.0E-7) {
			side = new Vec3(1.0, 0.0, 0.0);
		}
		return side.normalize();
	}

	private static void addRibbonVertex(VertexConsumer consumer, PoseStack.Pose pose, Vec3 point,
			float u, int alpha, int light) {
		consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
				.setUv(u, 0.0f)
				.setColor(154, 142, 172, alpha)
				.setLight(light)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setNormal(pose, 0.0f, 1.0f, 0.0f);
	}

	private static final class TrailBuffer {
		private final Deque<Vec3> positions = new ArrayDeque<>();
		private Vec3 lastPosition;
		private int lastTick = Integer.MIN_VALUE;
	}

	/** GeckoLib's state bag plus the vanilla render data needed by the custom ribbon pass. */
	public static final class State extends EntityRenderState implements GeoRenderState {
		private final Map<DataTicket<?>, Object> geckolibData = new Reference2ObjectOpenHashMap<>();
		private float age;
		private int seed;
		private float fadeAlpha = 1.0f;
		private Vec3 center = Vec3.ZERO;
		private List<Vec3> trail = List.of();

		@Override
		public <D> void addGeckolibData(DataTicket<D> dataTicket, @Nullable D data) {
			geckolibData.put(dataTicket, data);
		}

		@Override
		public boolean hasGeckolibData(DataTicket<?> dataTicket) {
			return geckolibData.containsKey(dataTicket);
		}

		@Override
		public <D> D getGeckolibData(DataTicket<D> dataTicket) {
			Object data = geckolibData.get(dataTicket);
			return data == null ? null : (D) data;
		}

		@Override
		public Map<DataTicket<?>, Object> getDataMap() {
			return geckolibData;
		}
	}
}
