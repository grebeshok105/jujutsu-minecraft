package jujutsu.mod.client.fx;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.ui.UiRender;

/**
 * Hand-drawn cursed-energy HUD for Nobara's Straw Doll kit. Fully custom render (rounded panel,
 * gradient fill, glow, mark pips) built on the shared {@link UiRender} primitives, not vanilla bars.
 */
public final class CursedEnergyHud {
	private static float current;
	private static float max = 100.0f;
	private static float displayed;
	private static int linkedMarks;
	private static boolean linked;
	private static long lastUpdateMillis;

	private CursedEnergyHud() {}

	public static void register() {
		HudElementRegistry.attachElementAfter(
				VanillaHudElements.MISC_OVERLAYS,
				JujutsuMod.id("cursed_energy_hud"),
				CursedEnergyHud::render
		);
	}

	public static void update(float value, float maximum, int marks, boolean isLinked) {
		current = value;
		max = Math.max(1.0f, maximum);
		linkedMarks = marks;
		linked = isLinked;
		lastUpdateMillis = System.currentTimeMillis();
	}

	private static void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (!NobaraHudState.holdingNobaraKit(client.player) && current >= max - 0.5f && !linked) {
			return;
		}

		// Smoothly chase the true value so spends/regen glide instead of snapping.
		float delta = tickCounter.getGameTimeDeltaTicks();
		displayed += (current - displayed) * Math.min(1.0f, 0.28f * delta + 0.05f);
		float fraction = Math.max(0.0f, Math.min(1.0f, displayed / max));

		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();
		int width = 128;
		int height = 34;
		int x = screenW - width - 10;
		int y = screenH - height - 48;
		long now = System.currentTimeMillis();
		float appear = Math.min(1.0f, (now - lastUpdateMillis > 4000 ? 4000 : now - lastUpdateMillis) / 220.0f);

		// Panel: soft shadow, dark glass body, hairline top accent.
		UiRender.shadow(graphics, x - 2, y - 2, width + 4, height + 4, 6, 0x66000000);
		UiRender.roundedRect(graphics, x, y, width, height, 6, 0xE6100612);
		UiRender.roundedRect(graphics, x, y, width, height, 6, 0x00000000, 0x33FF3E8F);
		UiRender.horizontalLine(graphics, x + 8, x + width - 8, y + 2, 0x99FF4DA6);

		// Label.
		Font font = client.font;
		graphics.drawString(font, Component.translatable("hud.jujutsumod.cursed_energy"), x + 8, y + 5, 0xFFE6C7FF, false);
		String amount = (int) displayed + "/" + (int) max;
		graphics.drawString(font, amount, x + width - 8 - font.width(amount), y + 5, 0xFFB98CFF, false);

		// Energy bar track + fill (crimson->fuchsia gradient), with a bright leading edge.
		int trackX = x + 8;
		int trackY = y + 18;
		int trackW = width - 16;
		int trackH = 6;
		UiRender.roundedRect(graphics, trackX, trackY, trackW, trackH, 3, 0xFF241026);
		int fillW = Math.round(trackW * fraction);
		if (fillW > 0) {
			UiRender.horizontalGradient(graphics, trackX, trackY + 1, fillW, trackH - 2, 0xFFB01E4B, 0xFFFF54C8);
			int edge = trackX + fillW;
			float pulse = 0.6f + 0.4f * (float) Math.sin(now / 130.0);
			int glow = ((int) (150 * pulse) << 24) | 0x00FFC8F0;
			UiRender.fill(graphics, edge - 1, trackY, 2, trackH, glow);
		}

		// Resonance link readout: mark pips + "LINKED" tag.
		if (linked) {
			int pipY = y + 27;
			graphics.drawString(font, Component.translatable("hud.jujutsumod.resonance_linked"), x + 8, pipY, 0xFFFF7AD0, false);
			int pipX = x + width - 8 - 4;
			for (int i = 0; i < 4; i++) {
				boolean on = i < linkedMarks;
				int color = on ? 0xFFFF54C8 : 0xFF3A2036;
				UiRender.fill(graphics, pipX - i * 7, pipY + 1, 4, 4, color);
			}
		}

		if (appear < 1.0f) {
			int fade = (int) ((1.0f - appear) * 160) << 24;
			UiRender.roundedRect(graphics, x, y, width, height, 6, fade);
		}
	}
}
