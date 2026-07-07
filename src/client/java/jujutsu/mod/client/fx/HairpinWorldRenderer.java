package jujutsu.mod.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.fx.HairpinTimeline;

public final class HairpinWorldRenderer {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	private static final int BLOOD_BLACK_R = 38;
	private static final int BLOOD_BLACK_G = 3;
	private static final int BLOOD_BLACK_B = 10;
	private static final int OXBLOOD_R = 58;
	private static final int OXBLOOD_G = 5;
	private static final int OXBLOOD_B = 15;
	private static final int CURSED_BLUE_R = 32;
	private static final int CURSED_BLUE_G = 92;
	private static final int CURSED_BLUE_B = 184;
	private static final int CURSED_BLUE_EDGE_R = 96;
	private static final int CURSED_BLUE_EDGE_G = 178;
	private static final int CURSED_BLUE_EDGE_B = 255;

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

		for (NobaraNailFlightManager.Prepared prepared : NobaraNailFlightManager.activePrepared()) {
			renderPrepared(consumer, context.world().getEntity(prepared.payload().playerEntityId()), cameraPosition, gameTime, partialTick, prepared);
		}
		for (NobaraNailFlightManager.Flight flight : NobaraNailFlightManager.activeFlights()) {
			renderFlight(consumer, flight, cameraPosition, gameTime, partialTick);
		}

		for (HairpinPlayback playback : HairpinPlaybackManager.activePlaybacks()) {
			HairpinTimeline.Phase phase = playback.phase(gameTime);
			if (phase == HairpinTimeline.Phase.DONE) {
				continue;
			}
			renderPlayback(consumer, playback, cameraPosition, phase, playback.progressInPhase(gameTime, partialTick));
		}
	}

	private static void renderPrepared(VertexConsumer consumer, Entity entity, Vec3 cameraPosition, long gameTime, float partialTick, NobaraNailFlightManager.Prepared prepared) {
		if (entity == null) {
			return;
		}
		float fade = prepared.fade(gameTime, partialTick);
		if (fade <= 0.01f) {
			return;
		}
		Vec3 center = entity.position().add(0.0, entity.getBbHeight() * 0.72, 0.0);
		int count = Math.max(0, Math.min(4, prepared.payload().nailCount()));
		double spin = (gameTime + partialTick + prepared.payload().seed() * 0.031) * 0.16;
		for (int index = 0; index < count; index++) {
			double angle = spin + index * Math.PI * 0.5;
			Vec3 offset = new Vec3(Math.cos(angle) * 0.56, 0.18 * Math.sin(angle * 1.7), Math.sin(angle) * 0.56);
			Vec3 position = center.add(offset).subtract(cameraPosition);
			Vec3 tangent = new Vec3(-Math.sin(angle), 0.08, Math.cos(angle)).normalize();
			renderBlueNailMarker(consumer, position, tangent, fade * 0.85f);
			Vec3 tail = position.subtract(tangent.scale(0.26));
			Vec3 head = position.add(tangent.scale(0.22));
			addRibbon(consumer, tail, head, sideVector(head.subtract(tail), position, 0.035f), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(120.0f * fade));
		}
	}

	private static void renderFlight(VertexConsumer consumer, NobaraNailFlightManager.Flight flight, Vec3 cameraPosition, long gameTime, float partialTick) {
		float progress = flight.progress(gameTime, partialTick);
		if (progress <= 0.0f) {
			return;
		}
		Vec3 target = flight.target();
		for (Vec3 nail : flight.nails()) {
			Vec3 current = nail.lerp(target, progress);
			Vec3 previous = nail.lerp(target, Math.max(0.0f, progress - 0.22f));
			Vec3 direction = safeDirection(target.subtract(nail));
			Vec3 start = previous.subtract(cameraPosition);
			Vec3 end = current.subtract(cameraPosition);
			float alpha = 1.0f - Math.max(0.0f, progress - 0.82f) / 0.18f;
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.08f);
			addRibbon(consumer, start, end, side, CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(190.0f * alpha));
			addRibbon(consumer, start.add(side.scale(0.72)), end.add(side.scale(0.72)), side.scale(0.32), CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(120.0f * alpha));
			renderBlueNailMarker(consumer, end, direction, alpha);
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

			addRibbon(consumer, start, end, side, BLOOD_BLACK_R, BLOOD_BLACK_G, BLOOD_BLACK_B, coreAlpha);
			if (phase == HairpinTimeline.Phase.HAMMER_SNAP || phase == HairpinTimeline.Phase.NAIL_IGNITION) {
				Vec3 edgeStart = start.lerp(end, 0.68);
				addRibbon(consumer, edgeStart.add(side.scale(0.55)), end.add(side.scale(0.55)), side.scale(0.18), OXBLOOD_R, OXBLOOD_G, OXBLOOD_B, edgeAlpha / 4);
			}

			if (phase == HairpinTimeline.Phase.HAIRPIN_BLOOM || phase == HairpinTimeline.Phase.AFTERGLOW) {
				Vec3 burstStart = target.add(direction.normalize().scale(0.08)).subtract(cameraPosition);
				Vec3 burstEnd = target.add(direction.normalize().scale(0.72 + progress * 0.32)).subtract(cameraPosition);
				Vec3 burstSide = sideVector(burstEnd.subtract(burstStart), burstStart.add(burstEnd).scale(0.5), width * (phase == HairpinTimeline.Phase.AFTERGLOW ? 0.85f : 1.9f));
				addRibbon(consumer, burstStart, burstEnd, burstSide, BLOOD_BLACK_R, BLOOD_BLACK_G, BLOOD_BLACK_B, coreAlpha);
			}
		}

		if (phase == HairpinTimeline.Phase.HAIRPIN_BLOOM) {
			renderShockwave(consumer, target.subtract(cameraPosition), phase, progress);
			renderFractureStar(consumer, target.subtract(cameraPosition), phase, progress);
		}
	}

	private static void renderShockwave(VertexConsumer consumer, Vec3 center, HairpinTimeline.Phase phase, float progress) {
		float bloom = phase == HairpinTimeline.Phase.HAIRPIN_BLOOM ? 1.0f - progress * 0.25f : 0.38f * (1.0f - progress);
		if (bloom <= 0.01f) {
			return;
		}

		double radius = phase == HairpinTimeline.Phase.HAIRPIN_BLOOM ? 0.42 + progress * 1.85 : 1.15 + progress * 0.75;
		float width = phase == HairpinTimeline.Phase.HAIRPIN_BLOOM ? 0.04f : 0.022f;
		int alpha = Math.min(220, Math.round(215.0f * bloom));
		int segments = 22;
		for (int index = 0; index < segments; index++) {
			double a0 = (Math.PI * 2.0 * index) / segments;
			double a1 = (Math.PI * 2.0 * (index + 1)) / segments;
			Vec3 start = center.add(Math.cos(a0) * radius, Math.sin(a0 * 2.0) * 0.035, Math.sin(a0) * radius);
			Vec3 end = center.add(Math.cos(a1) * radius, Math.sin(a1 * 2.0) * 0.035, Math.sin(a1) * radius);
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), width);
			addRibbon(consumer, start, end, side, OXBLOOD_R, OXBLOOD_G, OXBLOOD_B, alpha);
		}
	}

	private static void renderFractureStar(VertexConsumer consumer, Vec3 center, HairpinTimeline.Phase phase, float progress) {
		float fade = phase == HairpinTimeline.Phase.HAIRPIN_BLOOM ? 1.0f - progress : 0.28f * (1.0f - progress);
		if (fade <= 0.01f) {
			return;
		}

		int alpha = Math.min(230, Math.round(225.0f * fade));
		for (int index = 0; index < 10; index++) {
			double angle = index * 0.641 + 0.35;
			double length = 0.62 + (index % 4) * 0.2 + progress * 0.55;
			Vec3 direction = new Vec3(Math.cos(angle), (index % 2 == 0 ? 0.08 : -0.05), Math.sin(angle)).normalize();
			Vec3 start = center.add(direction.scale(0.08));
			Vec3 end = center.add(direction.scale(length));
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.018f + index % 2 * 0.01f);
			addRibbon(consumer, start, end, side, 22, 8, 12, alpha);
			addRibbon(consumer, start.add(side.scale(1.8)), end.add(side.scale(0.55)), side.scale(0.3), OXBLOOD_R, OXBLOOD_G, OXBLOOD_B, alpha / 4);
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

		Vec3 shaft = direction.scale(0.56);
		Vec3 head = anchor.subtract(direction.scale(0.1));
		Vec3 tip = anchor.add(shaft);
		Vec3 side = sideVector(shaft, anchor, 0.044f);
		Vec3 cross = safeDirection(shaft).cross(side).normalize().scale(0.034f);
		int steelAlpha = Math.round(alpha * 235.0f);
		int darkAlpha = Math.round(alpha * 220.0f);
		addRibbon(consumer, head, tip, side, 34, 38, 44, steelAlpha);
		addRibbon(consumer, head, tip, cross, 78, 84, 94, steelAlpha / 2);
		addRibbon(consumer, head.subtract(direction.scale(0.042)), head.add(direction.scale(0.042)), side.scale(1.85), 16, 7, 10, darkAlpha);
		addRibbon(consumer, head.add(direction.scale(0.1)), head.add(direction.scale(0.18)), side.scale(1.35), OXBLOOD_R, OXBLOOD_G, OXBLOOD_B, Math.round(alpha * 150.0f));
		addRibbon(consumer, tip.subtract(direction.scale(0.075)), tip.add(direction.scale(0.035)), side.scale(0.55), 112, 118, 126, Math.round(alpha * 155.0f));
	}

	private static void renderBlueNailMarker(VertexConsumer consumer, Vec3 anchor, Vec3 direction, float alpha) {
		if (alpha <= 0.01f) {
			return;
		}

		Vec3 shaft = direction.scale(0.62);
		Vec3 head = anchor.subtract(direction.scale(0.12));
		Vec3 tip = anchor.add(shaft);
		Vec3 side = sideVector(shaft, anchor, 0.04f);
		Vec3 cross = safeDirection(shaft).cross(side).normalize().scale(0.028f);
		addRibbon(consumer, head, tip, side, 28, 32, 40, Math.round(alpha * 235.0f));
		addRibbon(consumer, head, tip, cross, 86, 96, 112, Math.round(alpha * 135.0f));
		addRibbon(consumer, head.subtract(direction.scale(0.035)), head.add(direction.scale(0.06)), side.scale(1.65), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(alpha * 160.0f));
		addRibbon(consumer, tip.subtract(direction.scale(0.08)), tip.add(direction.scale(0.04)), side.scale(0.65), CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(alpha * 130.0f));
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
