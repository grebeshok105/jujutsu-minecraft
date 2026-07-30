package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.vfx.VfxPalette;

public final class HairpinWorldEffects {
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

	private HairpinWorldEffects() {}

	public static void renderHammerSend(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		int alpha = Math.min(230, Math.round(220.0f * fade));
		float spread = 0.16f + Math.min(4, intensity) * 0.025f;
		renderCyanRing(consumer, center, 0.42f + progress * 0.55f, 0.68f, Math.round(alpha * 0.72f), progress * 2.4f);
		for (int index = 0; index < 4; index++) {
			double centered = index - 1.5;
			Vec3 start = center.add(VfxWorldGeometry.EAST.scale(centered * spread)).add(VfxWorldGeometry.UP.scale(0.08 - Math.abs(centered) * 0.025));
			Vec3 end = start.add(new Vec3(0.0, 0.16 - index * 0.035, 1.1 + progress * 1.25));
			addFlashBlade(consumer, start, end, 0.022f, alpha);
		}
	}

	public static void renderEnlargeImpact(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		float scale = 1.0f + Math.min(4, intensity) * 0.16f;
		int alpha = Math.min(235, Math.round(225.0f * fade));
		if (progress < 0.42f) {
			float compression = progress < 0.32f ? progress / 0.32f : 1.0f;
			float radius = (1.75f - compression * 1.08f) * scale;
			renderCyanRing(consumer, center, radius, 0.42f, Math.round(alpha * 0.88f), -compression * 2.8f);
			renderCyanRing(consumer, center.add(VfxWorldGeometry.UP.scale(0.38f)), radius * 0.72f, 0.34f, Math.round(alpha * 0.58f), compression * 2.1f);
			return;
		}
		float release = (progress - 0.42f) / 0.58f;
		Vec3 side = VfxWorldGeometry.EAST.scale(1.9f * scale + release * 0.8f);
		Vec3 up = VfxWorldGeometry.UP.scale(1.55f * scale + release * 0.6f);
		Vec3 diagA = side.add(up);
		Vec3 diagB = side.subtract(up);
		addFlashBlade(consumer, center.subtract(diagA), center.add(diagA), 0.075f, alpha);
		addFlashBlade(consumer, center.subtract(diagB), center.add(diagB), 0.055f, alpha);
		renderCyanRing(consumer, center, 0.72f * scale + release * 1.0f, 0.42f, Math.round(alpha * 0.72f), release * 2.2f);
		renderCyanRing(consumer, center.add(VfxWorldGeometry.UP.scale(0.45f)), 0.54f * scale + release * 0.64f, 0.34f, Math.round(alpha * 0.48f), -release * 1.8f);
	}

	public static void renderExplosionImpact(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		float scale = 0.86f + Math.min(4, intensity) * 0.11f;
		int alpha = Math.min(230, Math.round(210.0f * fade));
		if (progress < 0.2f) {
			float implosion = progress / 0.2f;
			renderDarkRing(consumer, center, (1.1f - implosion * 0.8f) * scale, 0.72f, alpha, -implosion * 3.6f);
			renderCyanRing(consumer, center, (0.82f - implosion * 0.52f) * scale, 0.54f, Math.round(alpha * 0.64f), implosion * 3.0f);
			return;
		}
		float shell = (progress - 0.2f) / 0.8f;
		float innerShell = Math.max(0.0f, (shell - 0.12f) / 0.88f);
		renderCyanRing(consumer, center, scale * 0.32f + shell * 1.72f, 0.62f, alpha, shell * 2.6f);
		renderCyanRing(consumer, center, scale * 0.24f + innerShell * 1.28f, 0.44f, Math.round(alpha * 0.7f), -innerShell * 3.1f);
		for (int index = 0; index < 8; index++) {
			double angle = index * Math.PI * 2.0 / 8.0 + shell * 0.45;
			Vec3 direction = new Vec3(Math.cos(angle), (index % 2 == 0 ? 0.22 : -0.16), Math.sin(angle)).normalize();
			Vec3 start = center.add(direction.scale(0.1));
			float stagger = Math.max(0.0f, (shell - index * 0.025f) / (1.0f - index * 0.025f));
			Vec3 end = center.add(direction.scale(0.38f * scale + stagger * 1.22f));
			addFlashBlade(consumer, start, end, 0.026f, Math.round(alpha * 0.7f));
		}
	}

	public static void renderRitualBind(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		int alpha = Math.min(220, Math.round(205.0f * fade));
		float compression = 1.35f - progress * 0.72f;
		for (int ring = 0; ring < 3; ring++) {
			float radius = compression + ring * 0.22f;
			renderCyanRing(consumer, center.add(VfxWorldGeometry.UP.scale(ring * 0.16f - 0.18f)), radius, 0.36f, Math.round(alpha * (0.9f - ring * 0.2f)), -progress * (2.0f + ring));
		}
		for (int index = 0; index < 6; index++) {
			double angle = index * Math.PI * 2.0 / 6.0 + progress * 0.4;
			Vec3 start = center.add(VfxWorldGeometry.EAST.scale(Math.cos(angle) * compression)).add(VfxWorldGeometry.UP.scale(Math.sin(angle) * compression * 0.36f));
			addFlashBlade(consumer, start, center, 0.012f, Math.round(alpha * 0.58f));
		}
	}

	public static void renderDollStrike(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		int alpha = Math.min(245, Math.round(240.0f * fade));
		float length = 0.9f + Math.min(4, intensity) * 0.08f + progress * 0.35f;
		addFlashBlade(consumer, center.add(VfxWorldGeometry.UP.scale(0.62f)), center.subtract(VfxWorldGeometry.UP.scale(length)), 0.058f, alpha);
		addFlashBlade(consumer, center.subtract(VfxWorldGeometry.EAST.scale(0.48f)), center.add(VfxWorldGeometry.EAST.scale(0.48f)), 0.028f, Math.round(alpha * 0.72f));
		renderCyanRing(consumer, center, 0.28f + progress * 0.62f, 0.56f, Math.round(alpha * 0.64f), progress * 3.4f);
	}

	public static void renderResonanceRelease(VertexConsumer consumer, Vec3 center, int intensity, float progress, float fade) {
		int alpha = Math.min(248, Math.round(242.0f * fade));
		float scale = 1.02f + Math.min(4, intensity) * 0.18f;
		renderDarkRing(consumer, center, 0.38f * scale + progress * 0.52f, 0.82f, Math.round(alpha * 0.96f), -progress * 3.2f);
		renderCyanRing(consumer, center, 0.96f * scale + progress * 1.42f, 0.64f, Math.round(alpha * 0.78f), progress * 3.7f);
		for (int index = 0; index < 12; index++) {
			double angle = index * Math.PI * 2.0 / 12.0 + ((index & 1) == 0 ? 0.12 : -0.08);
			Vec3 direction = new Vec3(Math.cos(angle), (index % 3 - 1) * 0.22, Math.sin(angle)).normalize();
			Vec3 start = center.add(direction.scale(0.16f));
			Vec3 end = center.add(direction.scale(0.94f * scale + progress * (0.82f + (index % 3) * 0.18f)));
			addFlashBlade(consumer, start, end, 0.032f, Math.round(alpha * (0.62f + (index & 1) * 0.2f)));
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
			Vec3 start = center.add(VfxWorldGeometry.EAST.scale(Math.cos(a0) * radius)).add(VfxWorldGeometry.UP.scale(Math.sin(a0) * radius * depthScale));
			Vec3 end = center.add(VfxWorldGeometry.EAST.scale(Math.cos(a1) * radius)).add(VfxWorldGeometry.UP.scale(Math.sin(a1) * radius * depthScale));
			Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), 0.024f);
			VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(2.4), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(alpha * 0.48f));
			VfxWorldGeometry.addRibbon(consumer, start, end, side, CURSED_BLUE_EDGE_R, CURSED_BLUE_EDGE_G, CURSED_BLUE_EDGE_B, alpha);
		}
	}

	private static void renderDarkRing(VertexConsumer consumer, Vec3 center, float radius, float depthScale, int alpha, float phase) {
		if (alpha <= 0) {
			return;
		}
		int segments = 18;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = phase + segment * Math.PI * 2.0 / segments;
			double a1 = phase + (segment + 0.78) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(VfxWorldGeometry.EAST.scale(Math.cos(a0) * radius)).add(VfxWorldGeometry.UP.scale(Math.sin(a0) * radius * depthScale));
			Vec3 end = center.add(VfxWorldGeometry.EAST.scale(Math.cos(a1) * radius)).add(VfxWorldGeometry.UP.scale(Math.sin(a1) * radius * depthScale));
			Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), 0.042f);
			VfxWorldGeometry.addRibbon(consumer, start, end, side, CURSED_BLUE_DARK_R / 2, CURSED_BLUE_DARK_G / 2, CURSED_BLUE_DARK_B / 2, alpha);
		}
	}

	private static void addFlashBlade(VertexConsumer consumer, Vec3 start, Vec3 end, float width, int alpha) {
		if (alpha <= 0) {
			return;
		}
		Vec3 side = VfxWorldGeometry.sideVector(end.subtract(start), start.add(end).scale(0.5), width);
		VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(4.0), CURSED_BLUE_DARK_R, CURSED_BLUE_DARK_G, CURSED_BLUE_DARK_B, Math.round(alpha * 0.42f));
		VfxWorldGeometry.addRibbon(consumer, start, end, side.scale(1.55), CURSED_BLUE_R, CURSED_BLUE_G, CURSED_BLUE_B, alpha);
		VfxWorldGeometry.addRibbon(consumer, start.lerp(end, 0.18), end, side.scale(0.45), CURSED_BLUE_WHITE_R, CURSED_BLUE_WHITE_G, CURSED_BLUE_WHITE_B, Math.round(alpha * 0.42f));
	}
}
