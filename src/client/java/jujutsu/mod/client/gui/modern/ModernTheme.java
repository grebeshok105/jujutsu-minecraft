package jujutsu.mod.client.gui.modern;

/**
 * Visual tokens sampled from Rich clickgui screenshot + ClickGui.java layout.
 *
 * Screenshot sample (941×581, 2026-07-21):
 *   panel #161614, sidebar #171815, row #1B1B19, selected #212121
 *
 * ClickGui layout (fixed-gui-scale 2 space):
 *   sidebar 92 · header 38 · list 120 @ x=92 · settings 172 @ x=218
 *   BG ≈ 400 × 270 (list+settings+pad: 218+172+10)
 */
public final class ModernTheme {
	private ModernTheme() {}

	// ── geometry from ClickGui ────────────────────────────────────────
	public static final float BG_W = 400f;
	public static final float BG_H = 270f;
	public static final float RADIUS = 18f;
	public static final float SIDEBAR_W = 92f;
	public static final float HEADER_H = 38f;
	public static final float LIST_X = 92f;
	public static final float LIST_W = 120f;
	public static final float SETTINGS_X = 218f;
	public static final float SETTINGS_W = 172f;
	public static final float CONTENT_TOP = 38f;
	public static final float CONTENT_BOTTOM_PAD = 8f;
	public static final float COL_GAP = 6f;
	public static final float ROW_H = 28f;
	public static final float ROW_GAP = 4f;
	public static final float SIDE_ITEM_H = 24f;
	public static final float SIDE_ITEM_GAP = 3f;

	// ── palette (screenshot samples, monochrome — no amber chrome) ───
	public static final int SCRIM = 0x7D000000; // ~125 alpha like ClickGui dim
	public static final int PANEL = 0xF0161614;
	public static final int PANEL_DEEP = 0xF0121211;
	public static final int SIDEBAR = 0xF0171815;
	public static final int COLUMN = 0xF0141413;
	public static final int ROW = 0xF01B1B19;
	public static final int ROW_HOVER = 0xF01F1F1D;
	public static final int ROW_SELECTED = 0xF0212121;
	public static final int CHIP = 0xF01B1B1B;
	public static final int AVATAR = 0xF0212121;
	public static final int DIVIDER = 0x12FFFFFF;
	public static final int BORDER = 0x18FFFFFF;
	public static final int BORDER_SOFT = 0x0EFFFFFF;

	public static final int TEXT = 0xFFE4E4E4;
	public static final int TEXT_DIM = 0xFF9A9A9A;
	public static final int TEXT_MUTED = 0xFF6E6E6E;
	public static final int TEXT_SECTION = 0xFF5A5A5A;

	public static final int ONLINE = 0xFF4ADE80;
	public static final int STAR = 0xFFE8C547;
	/** Rare functional accent only (confirm) — still desaturated, not neon. */
	public static final int ACCENT = 0xFFD0D0D0;
	public static final int ACCENT_WARM = 0xFFC4A574;

	public static int withAlpha(int argb, float alpha) {
		int a = Math.round(clamp01(alpha) * ((argb >>> 24) & 0xFF));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	public static int lerpColor(int a, int b, float t) {
		t = clamp01(t);
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24)
				| (Math.round(ar + (br - ar) * t) << 16)
				| (Math.round(ag + (bg - ag) * t) << 8)
				| Math.round(ab + (bb - ab) * t);
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : Math.min(1f, t);
	}
}
