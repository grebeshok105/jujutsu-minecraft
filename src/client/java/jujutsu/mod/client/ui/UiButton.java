package jujutsu.mod.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Reusable custom button: dark glass pill that lifts, brightens and glows on hover, with a filled
 * accent variant for primary actions. All painted by hand via {@link UiRender}.
 */
public class UiButton extends UiElement {
	private final Component label;
	private final Runnable action;
	private boolean primary;

	public UiButton(float x, float y, float width, float height, Component label, Runnable action) {
		super(x, y, width, height);
		this.label = label;
		this.action = action;
	}

	public UiButton primary() {
		this.primary = true;
		return this;
	}

	@Override
	protected void draw(GuiGraphics g, int mouseX, int mouseY, float deltaTicks) {
		int bx = Math.round(x);
		int by = Math.round(y - press * 0.0f);
		int bw = Math.round(width);
		int bh = Math.round(height);
		int radius = bh / 2;

		if (hover > 0.01f) {
			UiRender.glow(g, bx, by, bw, bh, 5, UiTheme.ACCENT_RGB, hover * (primary ? 0.9f : 0.6f));
		}

		if (primary) {
			int top = UiRender.lerpColor(UiTheme.ACCENT_DEEP, UiTheme.ACCENT, 0.35f + hover * 0.4f);
			int bottom = UiRender.lerpColor(UiTheme.OXBLOOD, UiTheme.ACCENT_DEEP, 0.4f + hover * 0.3f);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, 0xFF000000);
			paintGradientPill(g, bx, by, bw, bh, radius, top, bottom);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, 0x00000000, UiRender.withAlpha(UiTheme.ACCENT_RGB, 0.5f + hover * 0.5f));
		} else {
			int body = UiRender.lerpColor(0xF2160B1C, 0xF2281232, hover);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, body,
					UiRender.lerpColor(UiTheme.BORDER, UiTheme.BORDER_STRONG, hover));
		}

		Font font = Minecraft.getInstance().font;
		int textColor = primary ? UiTheme.TEXT_ON_ACCENT : UiRender.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, hover);
		int tx = bx + (bw - font.width(label)) / 2;
		int ty = by + (bh - font.lineHeight) / 2 + 1;
		g.drawString(font, label, tx, ty, textColor, false);
	}

	private static void paintGradientPill(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
		for (int row = 0; row < h; row++) {
			float t = h == 1 ? 0.0f : row / (float) (h - 1);
			int color = UiRender.lerpColor(top, bottom, t);
			int inset = insetForRow(row, h, radius);
			UiRender.fill(g, x + inset, y + row, w - inset * 2, 1, color);
		}
	}

	private static int insetForRow(int row, int h, int r) {
		int dist;
		if (row < r) {
			dist = r - row;
		} else if (row >= h - r) {
			dist = row - (h - r) + 1;
		} else {
			return 0;
		}
		int k = r - dist;
		double v = Math.sqrt(Math.max(0, (double) r * r - (double) k * k));
		return Math.max(0, r - (int) Math.round(v));
	}

	@Override
	protected void onClick() {
		Minecraft.getInstance().getSoundManager().play(
				net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
						net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
		if (action != null) {
			action.run();
		}
	}
}
