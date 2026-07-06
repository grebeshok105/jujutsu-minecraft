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

	private static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		long elapsed = System.currentTimeMillis() - flashStartedAtMillis;
		if (elapsed < 0L || elapsed >= flashDurationMillis) {
			return;
		}

		float remaining = 1.0f - (elapsed / (float) flashDurationMillis);
		int alpha = Math.round(flashMaxAlpha * remaining * remaining);
		int color = (alpha << 24) | 0x005B101B;
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
	}
}
