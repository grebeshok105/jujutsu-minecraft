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

	private static void triggerCinematic(int durationMillis, int vignetteAlpha, int lineAlpha) {
		cinematicStartedAtMillis = System.currentTimeMillis();
		cinematicDurationMillis = Math.max(1, durationMillis);
		vignetteMaxAlpha = Math.max(0, Math.min(170, vignetteAlpha));
		speedLineAlpha = Math.max(0, Math.min(130, lineAlpha));
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
		int vignette = (vignetteAlpha << 24) | 0x000E0609;
		int lineColor = (lineAlpha << 24) | 0x0026030A;
		int hotLineColor = (Math.min(140, lineAlpha + 16) << 24) | 0x003A050F;

		int edgeX = Math.max(12, Math.round(width * 0.12f));
		int edgeY = Math.max(10, Math.round(height * 0.11f));
		graphics.fill(0, 0, width, edgeY, vignette);
		graphics.fill(0, height - edgeY, width, height, vignette);
		graphics.fill(0, 0, edgeX, height, vignette);
		graphics.fill(width - edgeX, 0, width, height, vignette);

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
	}
}
