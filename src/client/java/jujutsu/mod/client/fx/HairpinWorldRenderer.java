package jujutsu.mod.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.fx.HairpinTimeline;

public final class HairpinWorldRenderer {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);

	private HairpinWorldRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(HairpinWorldRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}

		long now = System.currentTimeMillis();
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());

		for (HairpinPlayback playback : HairpinPlaybackManager.activePlaybacks()) {
			HairpinTimeline.Phase phase = playback.phase(now);
			if (phase == HairpinTimeline.Phase.DONE) {
				continue;
			}
			renderPlayback(consumer, playback, cameraPosition, phase, playback.progressInPhase(now));
		}
	}

	private static void renderPlayback(VertexConsumer consumer, HairpinPlayback playback, Vec3 cameraPosition, HairpinTimeline.Phase phase, float progress) {
		Vec3 target = playback.target();
		float alpha = alphaFor(phase, progress);
		if (alpha <= 0.01f) {
			return;
		}

		float width = widthFor(phase, progress);
		for (Vec3 nail : playback.nails()) {
			Vec3 direction = target.subtract(nail);
			if (direction.lengthSqr() < 1.0E-5) {
				continue;
			}

			Vec3 start = nail.lerp(target, startLerpFor(phase, progress)).subtract(cameraPosition);
			Vec3 end = nail.lerp(target, endLerpFor(phase, progress)).subtract(cameraPosition);
			Vec3 side = sideVector(direction, width);
			int edgeAlpha = Math.min(170, Math.round(alpha * 255.0f));
			int coreAlpha = Math.min(210, Math.round(alpha * 255.0f));

			addRibbon(consumer, start, end, side, 91, 16, 27, coreAlpha);
			if (phase == HairpinTimeline.Phase.HAMMER_SNAP || phase == HairpinTimeline.Phase.NAIL_IGNITION) {
				Vec3 edgeStart = start.lerp(end, 0.68);
				addRibbon(consumer, edgeStart.add(side.scale(0.55)), end.add(side.scale(0.55)), side.scale(0.18), 91, 16, 27, edgeAlpha / 3);
			}

			if (phase == HairpinTimeline.Phase.HAIRPIN_BLOOM || phase == HairpinTimeline.Phase.AFTERGLOW) {
				Vec3 burstStart = target.add(direction.normalize().scale(0.08)).subtract(cameraPosition);
				Vec3 burstEnd = target.add(direction.normalize().scale(0.55 + progress * 0.18)).subtract(cameraPosition);
				addRibbon(consumer, burstStart, burstEnd, side.scale(phase == HairpinTimeline.Phase.AFTERGLOW ? 0.55 : 1.15), 18, 9, 12, coreAlpha);
			}
		}
	}

	private static float alphaFor(HairpinTimeline.Phase phase, float progress) {
		return switch (phase) {
			case PREP_FREEZE -> 0.18f;
			case HAMMER_SNAP -> 0.42f * (1.0f - progress * 0.4f);
			case NAIL_IGNITION -> 0.32f + progress * 0.26f;
			case HAIRPIN_BLOOM -> 0.72f * (1.0f - progress * 0.35f);
			case AFTERGLOW -> 0.3f * (1.0f - progress);
			case DONE -> 0.0f;
		};
	}

	private static float widthFor(HairpinTimeline.Phase phase, float progress) {
		return switch (phase) {
			case PREP_FREEZE -> 0.018f;
			case HAMMER_SNAP -> 0.028f;
			case NAIL_IGNITION -> 0.032f + progress * 0.022f;
			case HAIRPIN_BLOOM -> 0.072f;
			case AFTERGLOW -> 0.04f * (1.0f - progress * 0.4f);
			case DONE -> 0.0f;
		};
	}

	private static double startLerpFor(HairpinTimeline.Phase phase, float progress) {
		return switch (phase) {
			case PREP_FREEZE, HAMMER_SNAP -> 0.0;
			case NAIL_IGNITION -> Math.max(0.0, progress - 0.34);
			case HAIRPIN_BLOOM, AFTERGLOW -> 0.58;
			case DONE -> 1.0;
		};
	}

	private static double endLerpFor(HairpinTimeline.Phase phase, float progress) {
		return switch (phase) {
			case PREP_FREEZE -> 0.06;
			case HAMMER_SNAP -> 0.12;
			case NAIL_IGNITION -> Math.min(1.0, progress + 0.18);
			case HAIRPIN_BLOOM, AFTERGLOW -> 1.0;
			case DONE -> 1.0;
		};
	}

	private static Vec3 sideVector(Vec3 direction, float width) {
		Vec3 side = direction.normalize().cross(UP);
		if (side.lengthSqr() < 1.0E-5) {
			side = direction.normalize().cross(EAST);
		}
		return side.normalize().scale(width);
	}

	private static void addRibbon(VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
	}
}
