package jujutsu.mod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

public final class HairpinWorldRenderer {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
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
	private static final int FLASH_ENLARGE = 0;
	private static final int FLASH_EXPLOSION = 1;
	private static final List<ImpactFlash> IMPACT_FLASHES = new ArrayList<>();

	private HairpinWorldRenderer() {}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(HairpinWorldRenderer::render);
	}

	private static void render(WorldRenderContext context) {
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}

		Camera camera = context.camera();
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		renderImpactFlashes(consumer, camera.getPosition(), context.world().getGameTime(), context.tickCounter().getGameTimeDeltaPartialTick(false));
	}

	public static void triggerHairpinEnlarge(Vec3 origin, int marks) {
		triggerImpactFlash(origin, marks, FLASH_ENLARGE, 28);
	}

	public static void triggerHairpinExplosion(Vec3 origin, int marks) {
		triggerImpactFlash(origin, marks, FLASH_EXPLOSION, 18);
	}

	private static void triggerImpactFlash(Vec3 origin, int marks, int kind, int durationTicks) {
		if (Minecraft.getInstance().level == null) {
			return;
		}
		IMPACT_FLASHES.add(new ImpactFlash(origin, Math.max(1, marks), kind, Minecraft.getInstance().level.getGameTime(), durationTicks));
		if (IMPACT_FLASHES.size() > 48) {
			IMPACT_FLASHES.remove(0);
		}
	}

	private static void renderImpactFlashes(VertexConsumer consumer, Vec3 cameraPosition, long gameTime, float partialTick) {
		for (Iterator<ImpactFlash> iterator = IMPACT_FLASHES.iterator(); iterator.hasNext();) {
			ImpactFlash flash = iterator.next();
			float age = gameTime - flash.startGameTime() + partialTick;
			if (age >= flash.durationTicks()) {
				iterator.remove();
				continue;
			}
			float progress = Math.max(0.0f, Math.min(1.0f, age / flash.durationTicks()));
			float fade = 1.0f - progress;
			Vec3 center = flash.origin().subtract(cameraPosition);
			if (flash.kind() == FLASH_ENLARGE) {
				renderEnlargeImpact(consumer, center, flash.marks(), progress, fade);
			} else {
				renderExplosionImpact(consumer, center, flash.marks(), progress, fade);
			}
		}
	}

	private static void renderEnlargeImpact(VertexConsumer consumer, Vec3 center, int marks, float progress, float fade) {
		float scale = 1.0f + Math.min(4, marks) * 0.16f;
		int alpha = Math.min(235, Math.round(225.0f * fade));
		Vec3 side = EAST.scale(1.9f * scale + progress * 0.8f);
		Vec3 up = UP.scale(1.55f * scale + progress * 0.6f);
		Vec3 diagA = side.add(up);
		Vec3 diagB = side.subtract(up);
		addFlashBlade(consumer, center.subtract(diagA), center.add(diagA), 0.075f, alpha);
		addFlashBlade(consumer, center.subtract(diagB), center.add(diagB), 0.055f, alpha);
		renderCyanRing(consumer, center, 1.0f * scale + progress * 0.72f, 0.42f, Math.round(alpha * 0.72f), progress * 2.2f);
		renderCyanRing(consumer, center.add(UP.scale(0.45f)), 0.72f * scale + progress * 0.38f, 0.34f, Math.round(alpha * 0.48f), -progress * 1.8f);
	}

	private static void renderExplosionImpact(VertexConsumer consumer, Vec3 center, int marks, float progress, float fade) {
		float scale = 0.86f + Math.min(4, marks) * 0.11f;
		int alpha = Math.min(230, Math.round(210.0f * fade));
		renderCyanRing(consumer, center, scale + progress * 1.0f, 0.62f, alpha, progress * 2.6f);
		renderCyanRing(consumer, center, scale * 0.74f + progress * 0.52f, 0.44f, Math.round(alpha * 0.7f), -progress * 3.1f);
		for (int index = 0; index < 8; index++) {
			double angle = index * Math.PI * 2.0 / 8.0 + progress * 0.45;
			Vec3 direction = new Vec3(Math.cos(angle), (index % 2 == 0 ? 0.22 : -0.16), Math.sin(angle)).normalize();
			Vec3 start = center.add(direction.scale(0.1));
			Vec3 end = center.add(direction.scale(0.72f * scale + progress * 0.75f));
			addFlashBlade(consumer, start, end, 0.026f, Math.round(alpha * 0.7f));
		}
	}

	private static void renderCyanRing(VertexConsumer consumer, Vec3 center, float radius, float depthScale, int alpha, float phase) {
		if (alpha <= 0) {
			return;
		}
		int segments = 20;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = phase + segment * Math.PI * 2.0 / segments;
			double a1 = phase + (segment + 0.7) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(EAST.scale(Math.cos(a0) * radius)).add(UP.scale(Math.sin(a0) * radius * depthScale));
			Vec3 end = center.add(EAST.scale(Math.cos(a1) * radius)).add(UP.scale(Math.sin(a1) * radius * depthScale));
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.024f);
			addRibbon(consumer, start, end, side.scale(2.4), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(alpha * 0.48f));
			addRibbon(consumer, start, end, side, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, alpha);
		}
	}

	private static void addFlashBlade(VertexConsumer consumer, Vec3 start, Vec3 end, float width, int alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), width);
		addRibbon(consumer, start, end, side.scale(4.0), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(alpha * 0.42f));
		addRibbon(consumer, start, end, side.scale(1.55), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, alpha);
		addRibbon(consumer, start.lerp(end, 0.18), end, side.scale(0.45), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(alpha * 0.42f));
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

	private record ImpactFlash(Vec3 origin, int marks, int kind, long startGameTime, int durationTicks) {}
}
