package jujutsu.mod.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.fx.HairpinTimeline;
import jujutsu.mod.registry.JujutsuItems;

public final class HairpinWorldRenderer {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	private static final Vector3f MODEL_UP = new Vector3f(0.0f, 1.0f, 0.0f);
	private static final ItemStack NAIL_ITEM = new ItemStack(JujutsuItems.HAIRPIN_NAIL);
	private static final int BLOOD_BLACK_R = 38;
	private static final int BLOOD_BLACK_G = 3;
	private static final int BLOOD_BLACK_B = 10;
	private static final int OXBLOOD_R = 58;
	private static final int OXBLOOD_G = 5;
	private static final int OXBLOOD_B = 15;
	private static final int CURSED_BLUE_R = 12;
	private static final int CURSED_BLUE_G = 190;
	private static final int CURSED_BLUE_B = 255;
	private static final int CURSED_BLUE_EDGE_R = 64;
	private static final int CURSED_BLUE_EDGE_G = 236;
	private static final int CURSED_BLUE_EDGE_B = 255;
	private static final int CURSED_BLUE_DARK_R = 0;
	private static final int CURSED_BLUE_DARK_G = 74;
	private static final int CURSED_BLUE_DARK_B = 118;
	private static final int CURSED_BLUE_WHITE_R = 184;
	private static final int CURSED_BLUE_WHITE_G = 255;
	private static final int CURSED_BLUE_WHITE_B = 255;

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
		PoseStack matrices = context.matrixStack();
		ClientLevel world = context.world();
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());

		for (NobaraNailFlightManager.Prepared prepared : NobaraNailFlightManager.activePrepared()) {
			renderPrepared(consumer, matrices, consumers, world, cameraPosition, gameTime, partialTick, prepared);
		}
		for (NobaraNailFlightManager.Flight flight : NobaraNailFlightManager.activeFlights()) {
			renderFlight(consumer, matrices, consumers, world, flight, cameraPosition, gameTime, partialTick);
		}

		for (HairpinPlayback playback : HairpinPlaybackManager.activePlaybacks()) {
			HairpinTimeline.Phase phase = playback.phase(gameTime);
			if (phase == HairpinTimeline.Phase.DONE) {
				continue;
			}
			renderPlayback(consumer, matrices, consumers, world, playback, cameraPosition, phase, playback.progressInPhase(gameTime, partialTick), gameTime);
		}
		renderTargetMarks(consumer, world, cameraPosition, gameTime, partialTick);
	}

	private static void renderPrepared(VertexConsumer consumer, PoseStack matrices, MultiBufferSource consumers, ClientLevel world, Vec3 cameraPosition, long gameTime, float partialTick, NobaraNailFlightManager.Prepared prepared) {
		float fade = prepared.fade(gameTime, partialTick);
		if (fade <= 0.01f) {
			return;
		}
		Vec3 direction = safeDirection(prepared.direction());
		int index = 0;
		for (Vec3 nail : prepared.nails()) {
			Vec3 position = nail.subtract(cameraPosition);
			Vec3 tangent = direction;
			renderItemNail(matrices, consumers, world, nail, cameraPosition, tangent, 0.46f, prepared.payload().seed() + index);
			renderBlueFlameEnvelope(consumer, position, tangent, gameTime + index * 13L, fade, 0.72f, 0.18f, 3);
			Vec3 tail = position.subtract(tangent.scale(0.42));
			Vec3 head = position.add(tangent.scale(0.36));
			addRibbon(consumer, tail, head, sideVector(head.subtract(tail), position, 0.048f), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(130.0f * fade));
			addRibbon(consumer, tail, head, sideVector(head.subtract(tail), position, 0.024f), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(95.0f * fade));
			index++;
		}
	}

	private static void renderFlight(VertexConsumer consumer, PoseStack matrices, MultiBufferSource consumers, ClientLevel world, NobaraNailFlightManager.Flight flight, Vec3 cameraPosition, long gameTime, float partialTick) {
		float progress = flight.progress(gameTime, partialTick);
		if (progress <= 0.0f) {
			return;
		}
		Vec3 target = flight.target(world);
		int index = 0;
		for (Vec3 nail : flight.nails()) {
			float punchProgress = flightEase(progress);
			Vec3 current = nail.lerp(target, punchProgress);
			Vec3 previous = nail.lerp(target, Math.max(0.0f, punchProgress - 0.46f));
			Vec3 direction = safeDirection(target.subtract(nail));
			Vec3 start = previous.subtract(cameraPosition);
			Vec3 end = current.subtract(cameraPosition);
			float alpha = 1.0f - Math.max(0.0f, punchProgress - 0.88f) / 0.12f;
			Vec3 midpoint = start.add(end).scale(0.5);
			Vec3 side = sideVector(end.subtract(start), midpoint, 0.12f);
			addRibbon(consumer, start, end, side.scale(2.15), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(175.0f * alpha));
			addRibbon(consumer, start, end, side.scale(1.05), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(220.0f * alpha));
			addRibbon(consumer, start.add(side.scale(0.22)), end.add(side.scale(0.22)), side.scale(0.34), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(135.0f * alpha));
			renderBlueFlameEnvelope(consumer, end, direction, gameTime + index * 17L, alpha, 0.94f, 0.25f, 5);
			renderBlueFlameTongues(consumer, start, end, midpoint, gameTime + index * 19L, alpha, 5, 0.34f);
			renderItemNail(matrices, consumers, world, current, cameraPosition, direction, 0.54f, flight.payload().seed() + index);
			index++;
		}
	}

	private static float flightEase(float progress) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		return 1.0f - (float) Math.pow(1.0f - clamped, 3.0);
	}

	private static void renderBlueFlameEnvelope(VertexConsumer consumer, Vec3 center, Vec3 direction, long gameTime, float alpha, float length, float width, int tongues) {
		if (alpha <= 0.01f) {
			return;
		}
		Vec3 line = safeDirection(direction);
		Vec3 tail = center.subtract(line.scale(length * 0.5f));
		Vec3 head = center.add(line.scale(length * 0.5f));
		Vec3 side = sideVector(head.subtract(tail), center, width);
		Vec3 cross = line.cross(side);
		if (cross.lengthSqr() < 1.0E-5) {
			cross = sideVector(head.subtract(tail), center.add(0.0, 0.35, 0.0), width * 0.8f);
		} else {
			cross = cross.normalize().scale(width * 0.78f);
		}
		addRibbon(consumer, tail, head, side.scale(1.55), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(145.0f * alpha));
		addRibbon(consumer, tail.add(line.scale(length * 0.08f)), head, side.scale(0.76), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, Math.round(185.0f * alpha));
		addRibbon(consumer, tail, head, cross.scale(0.92), CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(115.0f * alpha));
		addRibbon(consumer, center.subtract(line.scale(length * 0.18f)), head.add(line.scale(length * 0.08f)), side.scale(0.22), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(42.0f * alpha));
		for (int index = 0; index < tongues; index++) {
			double offset = (index + 0.45) / (tongues + 0.3);
			double wave = Math.sin(gameTime * 0.55 + index * 1.73) * 0.5 + 0.5;
			Vec3 root = tail.lerp(head, Math.min(0.96, offset));
			Vec3 flare = (index % 2 == 0 ? side : cross).normalize().scale(width * (1.45 + wave * 0.72));
			Vec3 lick = root.add(flare).subtract(line.scale(length * (0.10 + wave * 0.08)));
			Vec3 tongueSide = sideVector(lick.subtract(root), root.add(lick).scale(0.5), 0.022f + width * 0.06f);
			addRibbon(consumer, root, lick, tongueSide, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(120.0f * alpha));
			addRibbon(consumer, root.lerp(lick, 0.34), lick, tongueSide.scale(0.42), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(34.0f * alpha));
		}
	}

	private static void renderBlueFlameTongues(VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 midpoint, long gameTime, float alpha, int count, float width) {
		Vec3 travel = end.subtract(start);
		if (travel.lengthSqr() < 1.0E-5 || alpha <= 0.01f) {
			return;
		}
		Vec3 side = sideVector(travel, midpoint, width);
		for (int index = 0; index < count; index++) {
			double offset = (index + 1.0) / (count + 1.0);
			double wave = Math.sin(gameTime * 0.7 + index * 1.9) * 0.5 + 0.5;
			Vec3 root = start.lerp(end, offset);
			Vec3 lick = root.add(side.scale((index % 2 == 0 ? 1.0 : -1.0) * (0.75 + wave * 0.55)));
			Vec3 tongueSide = sideVector(lick.subtract(root), root.add(lick).scale(0.5), 0.018f + 0.01f * (float) wave);
			addRibbon(consumer, root, lick, tongueSide, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, Math.round(105.0f * alpha));
			addRibbon(consumer, root.lerp(lick, 0.25), lick, tongueSide.scale(0.45), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(36.0f * alpha));
		}
	}

	private static void renderPlayback(VertexConsumer consumer, PoseStack matrices, MultiBufferSource consumers, ClientLevel world, HairpinPlayback playback, Vec3 cameraPosition, HairpinTimeline.Phase phase, float progress, long gameTime) {
		Vec3 target = playback.target(world);
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

			Vec3 travelDirection = safeDirection(direction);
			renderItemNail(matrices, consumers, world, nail, cameraPosition, travelDirection, 0.5f, playback.seed());
			if (phase == HairpinTimeline.Phase.PREP_FREEZE || phase == HairpinTimeline.Phase.HAMMER_SNAP || phase == HairpinTimeline.Phase.NAIL_IGNITION) {
				renderBlueFlameEnvelope(consumer, nail.subtract(cameraPosition), travelDirection, gameTime + playback.seed(), alpha * 0.75f, 0.78f, 0.16f + progress * 0.08f, 3);
			}
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
			renderFractureBurst(consumer, playback, target, target.subtract(cameraPosition), cameraPosition, progress);
		}
	}

	private static void renderFractureBurst(VertexConsumer consumer, HairpinPlayback playback, Vec3 target, Vec3 center, Vec3 cameraPosition, float progress) {
		float bloom = 1.0f - progress * 0.42f;
		if (bloom <= 0.01f) {
			return;
		}
		int alpha = Math.min(230, Math.round(230.0f * bloom));
		int nailIndex = 0;
		for (Vec3 nail : playback.nails()) {
			Vec3 vector = safeDirection(target.subtract(nail));
			Vec3 start = target.add(vector.scale(0.06)).subtract(cameraPosition);
			Vec3 end = target.add(vector.scale(0.95 + progress * (0.45 + nailIndex * 0.07))).subtract(cameraPosition);
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.13f + nailIndex * 0.012f);
			addRibbon(consumer, start, end, side, BLOOD_BLACK_R, BLOOD_BLACK_G, BLOOD_BLACK_B, alpha);
			addRibbon(consumer, start.add(side.scale(0.82)), end.add(side.scale(0.26)), side.scale(0.18), OXBLOOD_R, OXBLOOD_G, OXBLOOD_B, alpha / 3);
			Vec3 branchBase = start.lerp(end, 0.42 + (nailIndex % 2) * 0.18);
			Vec3 branch = vector.cross(UP);
			if (branch.lengthSqr() < 1.0E-5) {
				branch = vector.cross(EAST);
			}
			branch = branch.normalize().scale(0.42 + progress * 0.22);
			Vec3 branchEnd = branchBase.add((nailIndex % 2 == 0 ? branch : branch.scale(-1.0))).add(vector.scale(0.16));
			Vec3 branchSide = sideVector(branchEnd.subtract(branchBase), branchBase.add(branchEnd).scale(0.5), 0.035f);
			addRibbon(consumer, branchBase, branchEnd, branchSide, 22, 8, 12, alpha / 2);
			nailIndex++;
		}
		renderFractureStar(consumer, center, HairpinTimeline.Phase.HAIRPIN_BLOOM, progress);
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

	private static void renderTargetMarks(VertexConsumer consumer, ClientLevel world, Vec3 cameraPosition, long gameTime, float partialTick) {
		for (TargetMarkRenderManager.TargetMark mark : TargetMarkRenderManager.active(gameTime)) {
			Entity entity = world.getEntity(mark.targetEntityId());
			if (entity == null || !entity.isAlive()) {
				TargetMarkRenderManager.remove(mark.targetEntityId());
				continue;
			}
			float fade = mark.fade(gameTime, partialTick);
			if (fade <= 0.01f) {
				continue;
			}
			renderProjectJjkTargetMark(consumer, entity, cameraPosition, mark, gameTime, partialTick, fade);
		}
	}

	private static void renderProjectJjkTargetMark(VertexConsumer consumer, Entity entity, Vec3 cameraPosition, TargetMarkRenderManager.TargetMark mark, long gameTime, float partialTick, float fade) {
		float age = mark.age(gameTime, partialTick);
		float pulse = 0.78f + 0.22f * (float) Math.sin(age * 0.34f);
		Vec3 worldCenter = entity.position().add(0.0, entity.getBbHeight() * 0.52, 0.0);
		Vec3 center = worldCenter.subtract(cameraPosition);
		Vec3 view = safeDirection(cameraPosition.subtract(worldCenter));
		Vec3 side = view.cross(UP);
		if (side.lengthSqr() < 1.0E-5) {
			side = EAST;
		} else {
			side = side.normalize();
		}
		Vec3 depth = side.cross(UP);
		if (depth.lengthSqr() < 1.0E-5) {
			depth = new Vec3(0.0, 0.0, 1.0);
		} else {
			depth = depth.normalize();
		}
		float radius = Math.max(0.34f, entity.getBbWidth() * (0.68f + mark.marks() * 0.035f));
		float height = Math.max(0.74f, entity.getBbHeight() * 0.82f);
		int coreAlpha = Math.min(210, Math.round(172.0f * fade * pulse));
		int edgeAlpha = Math.min(235, Math.round(210.0f * fade * pulse));
		int darkAlpha = Math.min(150, Math.round(122.0f * fade));
		renderBlueBodyRing(consumer, center, side, depth, radius, -height * 0.36f, darkAlpha, coreAlpha, age * 0.018f);
		renderBlueBodyRing(consumer, center, side, depth, radius * 1.08f, 0.0f, darkAlpha, edgeAlpha, -age * 0.014f);
		renderBlueBodyRing(consumer, center, side, depth, radius * 0.94f, height * 0.34f, darkAlpha, coreAlpha, age * 0.012f);
		for (int index = 0; index < 6; index++) {
			double angle = index * Math.PI * 2.0 / 6.0 + age * 0.01;
			Vec3 horizontal = side.scale(Math.cos(angle) * radius).add(depth.scale(Math.sin(angle) * radius));
			Vec3 bottom = center.add(horizontal).add(UP.scale(-height * 0.42f));
			Vec3 top = center.add(horizontal.scale(0.76)).add(UP.scale(height * 0.42f));
			Vec3 thickness = sideVector(top.subtract(bottom), bottom.add(top).scale(0.5), 0.016f + mark.marks() * 0.002f);
			addRibbon(consumer, bottom, top, thickness.scale(2.1), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, darkAlpha);
			addRibbon(consumer, bottom.lerp(top, 0.18), top, thickness, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, edgeAlpha);
		}
		Vec3 snapStart = center.subtract(side.scale(radius * 0.62f)).add(UP.scale(height * 0.05f));
		Vec3 snapEnd = center.add(side.scale(radius * 0.62f)).subtract(UP.scale(height * 0.08f));
		addRibbon(consumer, snapStart, snapEnd, depth.scale(0.018f), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(70.0f * fade * pulse));
	}

	private static void renderBlueBodyRing(VertexConsumer consumer, Vec3 center, Vec3 side, Vec3 depth, float radius, float y, int darkAlpha, int edgeAlpha, float phase) {
		int segments = 18;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = phase + segment * Math.PI * 2.0 / segments;
			double a1 = phase + (segment + 0.78) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(UP.scale(y)).add(side.scale(Math.cos(a0) * radius)).add(depth.scale(Math.sin(a0) * radius * 0.72f));
			Vec3 end = center.add(UP.scale(y)).add(side.scale(Math.cos(a1) * radius)).add(depth.scale(Math.sin(a1) * radius * 0.72f));
			Vec3 thickness = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.018f);
			addRibbon(consumer, start, end, thickness.scale(2.4), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, darkAlpha);
			addRibbon(consumer, start, end, thickness, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, edgeAlpha);
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

	private static void renderItemNail(PoseStack matrices, MultiBufferSource consumers, ClientLevel world, Vec3 worldPosition, Vec3 cameraPosition, Vec3 direction, float scale, int seed) {
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		matrices.pushPose();
		Vec3 renderPosition = worldPosition.subtract(cameraPosition);
		matrices.translate(renderPosition.x, renderPosition.y, renderPosition.z);
		matrices.mulPose(new Quaternionf().rotationTo(MODEL_UP, toVector3f(safeDirection(direction))));
		matrices.mulPose(new Quaternionf().rotateY((float) ((seed & 3) * Math.PI * 0.5)));
		matrices.scale(scale, scale, scale);
		itemRenderer.renderStatic(
				NAIL_ITEM,
				ItemDisplayContext.NONE,
				LevelRenderer.getLightColor(world, BlockPos.containing(worldPosition)),
				OverlayTexture.NO_OVERLAY,
				matrices,
				consumers,
				world,
				seed
		);
		matrices.popPose();
	}

	private static Vector3f toVector3f(Vec3 vector) {
		return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z);
	}

	private static void addRibbon(VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
	}
}
