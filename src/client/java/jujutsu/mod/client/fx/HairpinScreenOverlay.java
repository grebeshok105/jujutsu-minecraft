package jujutsu.mod.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.fx.HairpinTimeline;

public final class HairpinScreenOverlay {
	private static long flashStartedAtMillis;
	private static int flashDurationMillis;
	private static int flashMaxAlpha;
	private static long cinematicStartedAtMillis;
	private static int cinematicDurationMillis;
	private static int vignetteMaxAlpha;
	private static int speedLineAlpha;

	private HairpinScreenOverlay() {}

	public static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				JujutsuMod.id("hairpin_impact_flash"),
				HairpinScreenOverlay::render
		);
	}

	public static void triggerFlash(int durationMillis, int maxAlpha) {
		flashStartedAtMillis = System.currentTimeMillis();
		flashDurationMillis = Math.max(1, durationMillis);
		flashMaxAlpha = Math.max(0, Math.min(180, maxAlpha));
	}

	public static void triggerCinematicBeat(HairpinTimeline.Phase phase) {
		switch (phase) {
			case PREP_FREEZE -> triggerCinematic(260, 46, 18);
			case HAMMER_SNAP -> triggerCinematic(180, 106, 88);
			case NAIL_IGNITION -> triggerCinematic(260, 98, 104);
			case HAIRPIN_BLOOM -> triggerCinematic(460, 148, 122);
			case AFTERGLOW -> triggerCinematic(700, 72, 10);
			case DONE -> {
			}
		}
	}

	public static void triggerProjectJjkHammer(float proximity) {
		triggerCinematic(220, scaledAlpha(150, proximity), scaledAlpha(126, proximity));
		triggerFlash(90, scaledAlpha(82, proximity));
	}

	public static void triggerProjectJjkImpact(float proximity) {
		triggerCinematic(380, scaledAlpha(170, proximity), scaledAlpha(130, proximity));
		triggerFlash(150, scaledAlpha(160, proximity));
	}

	private static void triggerCinematic(int durationMillis, int vignetteAlpha, int lineAlpha) {
		cinematicStartedAtMillis = System.currentTimeMillis();
		cinematicDurationMillis = Math.max(1, durationMillis);
		vignetteMaxAlpha = Math.max(0, Math.min(170, vignetteAlpha));
		speedLineAlpha = Math.max(0, Math.min(130, lineAlpha));
	}

	private static int scaledAlpha(int alpha, float proximity) {
		return Math.round(alpha * Math.max(0.0f, Math.min(1.0f, proximity)));
	}

	private static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		renderCinematic(graphics);
		renderFlash(graphics);
	}

	private static void renderFlash(GuiGraphics graphics) {
		long elapsed = System.currentTimeMillis() - flashStartedAtMillis;
		if (elapsed < 0L || elapsed >= flashDurationMillis) {
			return;
		}

		float remaining = 1.0f - (elapsed / (float) flashDurationMillis);
		int alpha = Math.round(flashMaxAlpha * remaining * remaining);
		int color = (alpha << 24) | 0x0026030A;
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
	}

	private static void renderCinematic(GuiGraphics graphics) {
		long elapsed = System.currentTimeMillis() - cinematicStartedAtMillis;
		if (elapsed < 0L || elapsed >= cinematicDurationMillis) {
			return;
		}

		float remaining = 1.0f - (elapsed / (float) cinematicDurationMillis);
		float ease = remaining * remaining;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int vignetteAlpha = Math.round(vignetteMaxAlpha * ease);
		int lineAlpha = Math.round(speedLineAlpha * ease);
		int lineColor = (lineAlpha << 24) | 0x0026030A;
		int hotLineColor = (Math.min(140, lineAlpha + 16) << 24) | 0x003A050F;

		renderEdgeVignette(graphics, width, height, vignetteAlpha, ease);

		int centerY = height / 2;
		int centerX = width / 2;
		int sweep = Math.round((elapsed % 180L) / 180.0f * width * 0.35f);
		for (int index = 0; index < 5; index++) {
			int y = centerY - 42 + index * 18;
			int x0 = Math.max(0, centerX - 160 + sweep + index * 12);
			int x1 = Math.min(width, x0 + 96 + index * 24);
			graphics.fill(x0, y, x1, y + 2, index == 2 ? hotLineColor : lineColor);
		}

		int rollOffset = Math.round(24.0f * ease);
		for (int index = 0; index < 4; index++) {
			int y = centerY - 70 + index * 42;
			int x0 = Math.max(0, centerX - 220 + index * 32);
			int x1 = Math.min(width, centerX + 220 + index * 32);
			int skew = (index % 2 == 0 ? rollOffset : -rollOffset);
			graphics.fill(Math.max(0, x0 + skew), y, Math.min(width, x1 + skew + 52), y + 1, lineColor);
		}
		renderEdgeTears(graphics, width, height, elapsed, lineAlpha, ease);
	}

	private static void renderEdgeVignette(GuiGraphics graphics, int width, int height, int alpha, float ease) {
		if (alpha <= 0) {
			return;
		}
		int layers = 7;
		int maxX = Math.max(18, Math.round(width * 0.16f));
		int maxY = Math.max(14, Math.round(height * 0.15f));
		for (int layer = 0; layer < layers; layer++) {
			float t = (layer + 1.0f) / layers;
			int layerAlpha = Math.round(alpha * (1.0f - t * 0.72f));
			int color = (layerAlpha << 24) | 0x00090508;
			int x = Math.max(1, Math.round(maxX * (1.0f - t)));
			int y = Math.max(1, Math.round(maxY * (1.0f - t)));
			graphics.fill(0, 0, width, y, color);
			graphics.fill(0, height - y, width, height, color);
			graphics.fill(0, 0, x, height, color);
			graphics.fill(width - x, 0, width, height, color);
		}

		int coreAlpha = Math.round(alpha * (0.32f + ease * 0.18f));
		int core = (coreAlpha << 24) | 0x0012070A;
		graphics.fill(0, 0, width, Math.max(4, maxY / 4), core);
		graphics.fill(0, height - Math.max(4, maxY / 4), width, height, core);
		graphics.fill(0, 0, Math.max(4, maxX / 4), height, core);
		graphics.fill(width - Math.max(4, maxX / 4), 0, width, height, core);
	}

	private static void renderEdgeTears(GuiGraphics graphics, int width, int height, long elapsed, int alpha, float ease) {
		if (alpha <= 0) {
			return;
		}
		int tearAlpha = Math.min(150, Math.round((alpha + 10) * ease * (0.74f + ease * 0.26f)));
		int blood = (tearAlpha << 24) | 0x002B0309;
		int black = (Math.min(170, tearAlpha + 18) << 24) | 0x00040204;
		int roll = Math.round((elapsed % 240L) / 240.0f * 18.0f);
		for (int index = 0; index < 6; index++) {
			boolean left = (index & 1) == 0;
			int y = 18 + index * Math.max(14, height / 8) + ((index % 3) - 1) * roll;
			if (y >= height - 12) {
				y = height - 20 - index * 7;
			}
			int len = 18 + (index % 3) * 9;
			int x0 = left ? 2 + index % 2 * 4 : width - len - 2 - index % 2 * 4;
			int x1 = x0 + len;
			graphics.fill(x0, y, x1, y + 2, black);
			graphics.fill(left ? x0 + len / 2 : x0 + 3, y + 2, left ? x0 + len / 2 + 2 : x0 + 5, y + 9, blood);
		}
	}
}
