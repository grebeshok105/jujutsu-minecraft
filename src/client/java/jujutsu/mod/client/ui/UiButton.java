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
		int upperH = Math.max(1, h / 3);
		int middleH = Math.max(1, h - upperH * 2);
		int lowerY = y + upperH + middleH;
		UiRender.roundedRect(g, x, y, w, h, radius, bottom);
		UiRender.fill(g, x + radius / 2, y + 1, w - radius, upperH, top);
		UiRender.fill(g, x + 2, y + upperH, w - 4, middleH, UiRender.lerpColor(top, bottom, 0.52f));
		UiRender.fill(g, x + radius / 2, lowerY, w - radius, Math.max(1, y + h - lowerY - 1), bottom);
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
