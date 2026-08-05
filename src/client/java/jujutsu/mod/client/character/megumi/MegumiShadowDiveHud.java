package jujutsu.mod.client.character.megumi;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import jujutsu.mod.client.vfx.VfxDirector;

/**
 * Megumi-owned full-screen veil for the first-person shadow dive, drawn under the crosshair layer
 * like the nausea overlay: pure black, alpha driven by the dive beats on the camera channel, so the
 * darkness always matches the camera curve. First-person only, like the camera offset itself — in
 * third person the body sink is the presentation and must stay visible. Data-gated: with no dive
 * entry for the camera entity the alpha is exactly zero and nothing is drawn.
 */
public final class MegumiShadowDiveHud {
	private static final int PURE_BLACK = 0x000000;

	private MegumiShadowDiveHud() {}

	public static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}
		float alpha = VfxDirector.diveFadeAlpha(tickCounter.getGameTimeDeltaPartialTick(false));
		if (alpha <= 0.001f) {
			return;
		}
		int color = (Math.round(alpha * 255.0f) << 24) | PURE_BLACK;
		graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), color);
	}
}
