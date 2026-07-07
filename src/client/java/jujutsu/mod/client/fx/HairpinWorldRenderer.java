package jujutsu.mod.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.fx.HairpinTimeline;

public final class HairpinWorldRenderer {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);

	private HairpinWorldRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(HairpinWorldRenderer::render);
		HairpinDebugLog.info("world renderer registered event=AFTER_ENTITIES");
	}

	private static void render(WorldRenderContext context) {
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}

		long gameTime = context.world().getGameTime();
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		Camera camera = context.camera();
		Vec3 cameraPosition = camera.getPosition();
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());

		for (HairpinPlayback playback : HairpinPlaybackManager.activePlaybacks()) {
			HairpinTimeline.Phase phase = playback.phase(gameTime);
			if (phase == HairpinTimeline.Phase.DONE) {
				continue;
			}
			renderPlayback(consumer, playback, cameraPosition, phase, playback.progressInPhase(gameTime, partialTick));
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

			renderNailMarker(consumer, nail.subtract(cameraPosition), safeDirection(direction), phase, progress);
			Vec3 start = nail.lerp(target, startLerpFor(phase, progress)).subtract(cameraPosition);
			Vec3 end = nail.lerp(target, endLerpFor(phase, progress)).subtract(cameraPosition);
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), width);
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
				Vec3 burstSide = sideVector(burstEnd.subtract(burstStart), burstStart.add(burstEnd).scale(0.5), width * (phase == HairpinTimeline.Phase.AFTERGLOW ? 0.9f : 1.65f));
				addRibbon(consumer, burstStart, burstEnd, burstSide, 91, 16, 27, coreAlpha);
			}
		}
	}

	private static void renderNailMarker(VertexConsumer consumer, Vec3 anchor, Vec3 direction, HairpinTimeline.Phase phase, float progress) {
		float alpha = switch (phase) {
			case PREP_FREEZE -> 0.95f;
			case HAMMER_SNAP, NAIL_IGNITION -> 1.0f;
			case HAIRPIN_BLOOM -> 0.9f * (1.0f - progress * 0.35f);
			case AFTERGLOW -> 0.45f * (1.0f - progress);
			case DONE -> 0.0f;
		};
		if (alpha <= 0.01f) {
			return;
		}

		Vec3 shaft = direction.scale(0.42);
		Vec3 head = anchor.subtract(direction.scale(0.08));
		Vec3 tip = anchor.add(shaft);
		Vec3 side = sideVector(shaft, anchor, 0.035f);
		Vec3 cross = safeDirection(shaft).cross(side).normalize().scale(0.035f);
		int steelAlpha = Math.round(alpha * 235.0f);
		int darkAlpha = Math.round(alpha * 210.0f);
		addRibbon(consumer, head, tip, side, 95, 102, 109, steelAlpha);
		addRibbon(consumer, head, tip, cross, 152, 161, 170, steelAlpha / 2);
		addRibbon(consumer, head.subtract(direction.scale(0.035)), head.add(direction.scale(0.035)), side.scale(1.7), 18, 9, 12, darkAlpha);
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
			case PREP_FREEZE -> 0.032f;
			case HAMMER_SNAP -> 0.05f;
			case NAIL_IGNITION -> 0.052f + progress * 0.036f;
			case HAIRPIN_BLOOM -> 0.13f;
			case AFTERGLOW -> 0.08f * (1.0f - progress * 0.4f);
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

	private static Vec3 sideVector(Vec3 direction, Vec3 cameraRelativeMidpoint, float width) {
		Vec3 line = direction.lengthSqr() < 1.0E-5 ? UP : direction.normalize();
		Vec3 view = cameraRelativeMidpoint.lengthSqr() < 1.0E-5 ? EAST : cameraRelativeMidpoint.normalize();
		Vec3 side = line.cross(view);
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(UP);
		}
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(EAST);
		}
		return side.normalize().scale(width);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? UP : vector.normalize();
	}

	private static void addRibbon(VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
	}
}
