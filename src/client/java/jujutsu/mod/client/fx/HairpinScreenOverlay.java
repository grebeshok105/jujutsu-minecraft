package jujutsu.mod.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import jujutsu.mod.JujutsuMod;

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
		int vignetteAlpha = Math.round((vignetteMaxAlpha + speedLineAlpha * 0.2f) * ease);
		renderSmoothEdgeVignette(graphics, width, height, vignetteAlpha, ease);
	}

	private static void renderSmoothEdgeVignette(GuiGraphics graphics, int width, int height, int alpha, float ease) {
		if (alpha <= 0) {
			return;
		}
		int layers = 28;
		int maxX = Math.max(34, Math.round(width * (0.22f + ease * 0.04f)));
		int maxY = Math.max(28, Math.round(height * (0.20f + ease * 0.04f)));
		for (int layer = 0; layer < layers; layer++) {
			float t = layer / (float) (layers - 1);
			float falloff = 1.0f - t;
			float strength = falloff * falloff * (0.16f + ease * 0.04f);
			int layerAlpha = Math.min(48, Math.round(alpha * strength));
			int color = (layerAlpha << 24) | 0x00090508;
			int x = Math.max(1, Math.round(maxX * falloff));
			int y = Math.max(1, Math.round(maxY * falloff));
			graphics.fill(0, 0, width, y, color);
			graphics.fill(0, height - y, width, height, color);
			graphics.fill(0, 0, x, height, color);
			graphics.fill(width - x, 0, width, height, color);
		}

		int coreAlpha = Math.round(alpha * (0.18f + ease * 0.16f));
		int core = (coreAlpha << 24) | 0x00100609;
		int coreX = Math.max(6, maxX / 5);
		int coreY = Math.max(6, maxY / 5);
		graphics.fill(0, 0, width, coreY, core);
		graphics.fill(0, height - coreY, width, height, core);
		graphics.fill(0, 0, coreX, height, core);
		graphics.fill(width - coreX, 0, width, height, core);
	}
}
