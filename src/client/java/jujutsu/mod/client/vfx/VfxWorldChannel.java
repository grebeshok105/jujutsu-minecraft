package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.VfxAnchorResolver;
import jujutsu.mod.vfx.VfxCue;

public final class VfxWorldChannel {
	private static final int MAX_IMPACT_FLASHES = 48;
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	private static final int CURSED_BLUE_R = VfxPalette.CURSED_BLUE_R;
	private static final int CURSED_BLUE_G = VfxPalette.CURSED_BLUE_G;
	private static final int CURSED_BLUE_B = VfxPalette.CURSED_BLUE_B;
	private static final int CURSED_BLUE_EDGE_R = VfxPalette.CURSED_BLUE_EDGE_R;
	private static final int CURSED_BLUE_EDGE_G = VfxPalette.CURSED_BLUE_EDGE_G;
	private static final int CURSED_BLUE_EDGE_B = VfxPalette.CURSED_BLUE_EDGE_B;
	private static final int CURSED_BLUE_DARK_R = VfxPalette.CURSED_BLUE_DARK_R;
	private static final int CURSED_BLUE_DARK_G = VfxPalette.CURSED_BLUE_DARK_G;
	private static final int CURSED_BLUE_DARK_B = VfxPalette.CURSED_BLUE_DARK_B;
	private static final int CURSED_BLUE_WHITE_R = VfxPalette.CURSED_BLUE_WHITE_R;
	private static final int CURSED_BLUE_WHITE_G = VfxPalette.CURSED_BLUE_WHITE_G;
	private static final int CURSED_BLUE_WHITE_B = VfxPalette.CURSED_BLUE_WHITE_B;
	private final List<ImpactFlash> impactFlashes = new ArrayList<>();

	public void triggerImpact(VfxCue cue, ImpactStyle style, int durationTicks) {
		impactFlashes.add(new ImpactFlash(cue, style, Math.max(1, durationTicks)));
		if (impactFlashes.size() > MAX_IMPACT_FLASHES) {
			impactFlashes.remove(0);
		}
	}

	void render(WorldRenderContext context) {
		MultiBufferSource consumers = context.consumers();
		if (consumers == null) {
			return;
		}
		Camera camera = context.camera();
		VertexConsumer consumer = consumers.getBuffer(RenderType.lightning());
		renderImpactFlashes(consumer, camera.getPosition(), context, context.tickCounter().getGameTimeDeltaPartialTick(false));
	}

	void clear() {
		impactFlashes.clear();
	}

	private void renderImpactFlashes(VertexConsumer consumer, Vec3 cameraPosition, WorldRenderContext context, float partialTick) {
		for (Iterator<ImpactFlash> iterator = impactFlashes.iterator(); iterator.hasNext();) {
			ImpactFlash flash = iterator.next();
			float age = context.world().getGameTime() - flash.cue().startGameTime() + partialTick;
			if (age >= flash.durationTicks()) {
				iterator.remove();
				continue;
			}
			float progress = Math.max(0.0f, Math.min(1.0f, age / flash.durationTicks()));
			float fade = 1.0f - progress;
			Vec3 origin = VfxAnchorResolver.resolve(flash.cue(), entityId -> {
				Entity anchor = context.world().getEntity(entityId);
				return anchor == null ? null : anchor.position();
			});
			Vec3 center = origin.subtract(cameraPosition);
			if (flash.style() == ImpactStyle.ENLARGE) {
				renderEnlargeImpact(consumer, center, Math.max(1, flash.cue().intensity()), progress, fade);
			} else {
				renderExplosionImpact(consumer, center, Math.max(1, flash.cue().intensity()), progress, fade);
			}
		}
	}

	private static void renderEnlargeImpact(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		float scale = 1.0f + Math.min(4, intensity) * 0.16f;
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

	private static void renderExplosionImpact(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		float scale = 0.86f + Math.min(4, intensity) * 0.11f;
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

	public enum ImpactStyle {
		ENLARGE,
		EXPLOSION
	}

	private record ImpactFlash(VfxCue cue, ImpactStyle style, int durationTicks) {}
}
