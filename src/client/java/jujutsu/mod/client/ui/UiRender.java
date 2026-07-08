package jujutsu.mod.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Low-level hand-drawn UI primitives built purely on {@link GuiGraphics#fill}. Everything the custom
 * UI kit draws — rounded panels, soft shadows, gradients, glows — is composed from these, so no
 * vanilla widget textures or nine-slice sprites are involved. Colors are ARGB ints.
 */
public final class UiRender {
	private UiRender() {}

	public static void fill(GuiGraphics g, int x, int y, int w, int h, int argb) {
		if (w <= 0 || h <= 0 || (argb >>> 24) == 0) {
			return;
		}
		g.fill(x, y, x + w, y + h, argb);
	}

	/** Vertical gradient (top -> bottom). */
	public static void verticalGradient(GuiGraphics g, int x, int y, int w, int h, int top, int bottom) {
		if (w <= 0 || h <= 0) {
			return;
		}
		g.fillGradient(x, y, x + w, y + h, top, bottom);
	}

	/** Horizontal gradient (left -> right), drawn as thin vertical strips. */
	public static void horizontalGradient(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
		if (w <= 0 || h <= 0) {
			return;
		}
		for (int i = 0; i < w; i++) {
			float t = w == 1 ? 0.0f : i / (float) (w - 1);
			g.fill(x + i, y, x + i + 1, y + h, lerpColor(left, right, t));
		}
	}

	public static void horizontalLine(GuiGraphics g, int x0, int x1, int y, int argb) {
		g.fill(Math.min(x0, x1), y, Math.max(x0, x1), y + 1, argb);
	}

	/** Solid rounded rectangle with a simple stepped corner approximation. */
	public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int argb) {
		roundedRect(g, x, y, w, h, radius, argb, 0);
	}

	/**
	 * Rounded rectangle with optional 1px border. Corners use a small per-row inset table so the
	 * silhouette reads as a smooth radius rather than a hard box.
	 */
	public static void roundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int fillArgb, int borderArgb) {
		if (w <= 0 || h <= 0) {
			return;
		}
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		boolean hasFill = (fillArgb >>> 24) != 0;
		boolean hasBorder = (borderArgb >>> 24) != 0;
		for (int row = 0; row < h; row++) {
			int inset = cornerInset(row, h, r);
			int rowX = x + inset;
			int rowW = w - inset * 2;
			if (rowW <= 0) {
				continue;
			}
			if (hasFill) {
				g.fill(rowX, y + row, rowX + rowW, y + row + 1, fillArgb);
			}
			if (hasBorder) {
				boolean edgeRow = row == 0 || row == h - 1 || inset != cornerInset(row - 1, h, r) || inset != cornerInset(row + 1, h, r);
				if (edgeRow) {
					g.fill(rowX, y + row, rowX + rowW, y + row + 1, borderArgb);
				} else {
					g.fill(rowX, y + row, rowX + 1, y + row + 1, borderArgb);
					g.fill(rowX + rowW - 1, y + row, rowX + rowW, y + row + 1, borderArgb);
				}
			}
		}
	}

	/** Layered translucent halo around a rectangle to fake a soft drop shadow / glow. */
	public static void shadow(GuiGraphics g, int x, int y, int w, int h, int spread, int argb) {
		int baseAlpha = (argb >>> 24) & 0xff;
		int rgb = argb & 0x00FFFFFF;
		for (int i = spread; i >= 1; i--) {
			int alpha = Math.max(1, baseAlpha * (spread - i + 1) / (spread * 3));
			int color = (alpha << 24) | rgb;
			roundedRect(g, x - i, y - i, w + i * 2, h + i * 2, 6 + i, color);
		}
	}

	public static void fastShadow(GuiGraphics g, int x, int y, int w, int h, int argb) {
		int baseAlpha = (argb >>> 24) & 0xff;
		int rgb = argb & 0x00FFFFFF;
		fill(g, x + 3, y + h, w - 6, 3, ((baseAlpha / 2) << 24) | rgb);
		fill(g, x + 5, y + h + 3, w - 10, 3, ((baseAlpha / 4) << 24) | rgb);
		fill(g, x + w, y + 4, 3, h - 8, ((baseAlpha / 4) << 24) | rgb);
	}

	public static void fastGlow(GuiGraphics g, int x, int y, int w, int h, int accentRgb, float intensity) {
		int alpha = Math.max(0, Math.min(110, Math.round(72.0f * intensity)));
		if (alpha <= 0) {
			return;
		}
		int color = (alpha << 24) | (accentRgb & 0x00FFFFFF);
		horizontalLine(g, x + 6, x + w - 6, y - 1, color);
		horizontalLine(g, x + 6, x + w - 6, y + h, color);
		fill(g, x - 1, y + 6, 1, h - 12, color);
		fill(g, x + w, y + 6, 1, h - 12, color);
	}

	/** Outer glow ring in a single accent color, brightest at the edge. */
	public static void glow(GuiGraphics g, int x, int y, int w, int h, int spread, int accentRgb, float intensity) {
		for (int i = spread; i >= 1; i--) {
			float falloff = (spread - i + 1) / (float) spread;
			int alpha = Math.max(0, Math.min(255, (int) (120 * intensity * falloff)));
			if (alpha == 0) {
				continue;
			}
			roundedRect(g, x - i, y - i, w + i * 2, h + i * 2, 8 + i, (alpha << 24) | (accentRgb & 0x00FFFFFF));
		}
	}

	public static int lerpColor(int from, int to, float t) {
		t = Math.max(0.0f, Math.min(1.0f, t));
		int fa = (from >>> 24) & 0xff, fr = (from >>> 16) & 0xff, fg = (from >>> 8) & 0xff, fb = from & 0xff;
		int ta = (to >>> 24) & 0xff, tr = (to >>> 16) & 0xff, tg = (to >>> 8) & 0xff, tb = to & 0xff;
		int a = (int) (fa + (ta - fa) * t);
		int r = (int) (fr + (tr - fr) * t);
		int gg = (int) (fg + (tg - fg) * t);
		int b = (int) (fb + (tb - fb) * t);
		return (a << 24) | (r << 16) | (gg << 8) | b;
	}

	public static int withAlpha(int rgb, float alpha) {
		int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
		return (a << 24) | (rgb & 0x00FFFFFF);
	}

	private static int cornerInset(int row, int h, int r) {
		if (r <= 0) {
			return 0;
		}
		int dist;
		if (row < r) {
			dist = r - row;
		} else if (row >= h - r) {
			dist = row - (h - r) + 1;
		} else {
			return 0;
		}
		// Circle-ish inset: inset = r - sqrt(r^2 - (r-dist)^2).
		int k = r - dist;
		double v = Math.sqrt(Math.max(0, (double) r * r - (double) k * k));
		return Math.max(0, r - (int) Math.round(v));
	}
}
