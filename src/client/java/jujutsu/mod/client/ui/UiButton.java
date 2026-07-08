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
	private int accentRgb = UiTheme.ACCENT_RGB;

	public UiButton(float x, float y, float width, float height, Component label, Runnable action) {
		super(x, y, width, height);
		this.label = label;
		this.action = action;
	}

	public UiButton primary() {
		this.primary = true;
		return this;
	}

	public void setAccentRgb(int accentRgb) {
		this.accentRgb = accentRgb & 0x00FFFFFF;
	}

	@Override
	protected void draw(GuiGraphics g, int mouseX, int mouseY, float deltaTicks) {
		int bx = Math.round(x);
		int by = Math.round(y - press * 0.0f);
		int bw = Math.round(width);
		int bh = Math.round(height);
		int radius = bh / 2;

		UiRender.fastShadow(g, bx, by + 1, bw, bh, 0x55000000);
		if (hover > 0.01f) {
			UiRender.fastGlow(g, bx, by, bw, bh, accentRgb, hover * (primary ? 0.72f : 0.42f));
		}

		if (primary) {
			int accent = 0xFF000000 | accentRgb;
			int top = UiRender.lerpColor(0xFF3A1E16, accent, 0.62f + hover * 0.18f);
			int bottom = UiRender.lerpColor(0xFF120B10, accent, 0.34f + hover * 0.14f);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, 0xFF000000);
			paintGradientPill(g, bx, by, bw, bh, radius, top, bottom);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, 0x00000000, UiRender.withAlpha(accentRgb, 0.48f + hover * 0.36f));
		} else {
			int body = UiRender.lerpColor(0xF2120E16, 0xF21D1721, hover);
			UiRender.roundedRect(g, bx, by, bw, bh, radius, body,
					UiRender.lerpColor(0x334A4056, UiRender.withAlpha(accentRgb, 0.46f), hover));
		}

		Font font = Minecraft.getInstance().font;
		int textColor = primary ? primaryTextColor() : UiRender.lerpColor(UiTheme.TEXT_MUTED, UiTheme.TEXT, hover);
		int tx = bx + (bw - font.width(label)) / 2;
		int ty = by + (bh - font.lineHeight) / 2 + 1;
		g.drawString(font, label, tx, ty, textColor, false);
	}

	private int primaryTextColor() {
		int r = (accentRgb >>> 16) & 0xff;
		int g = (accentRgb >>> 8) & 0xff;
		int b = accentRgb & 0xff;
		int luminance = r * 299 + g * 587 + b * 114;
		return luminance < 92000 ? UiTheme.TEXT : UiTheme.TEXT_ON_ACCENT;
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
