package jujutsu.mod.client.gui.modern;

/**
 * Rich-Modern clickgui palette (charcoal glass) adapted for jujutsumod.
 * Sampled from the user screenshot + Rich ClickGui layout numbers.
 */
public final class ModernTheme {
	private ModernTheme() {}

	// Shell
	public static final int SCRIM = 0xA0000000;
	public static final int PANEL = 0xF018181B;
	public static final int PANEL_EDGE = 0x28FFFFFF;
	public static final int PANEL_GLOW = 0x28000000;

	// Columns
	public static final int SIDEBAR = 0xF0141416;
	public static final int COLUMN = 0xF0161619;
	public static final int CARD = 0xF0202024;
	public static final int CARD_HOVER = 0xF028282E;
	public static final int CARD_SELECTED = 0xF02C2C32;
	public static final int DIVIDER = 0x14FFFFFF;

	// Text
	public static final int TEXT = 0xFFECECEF;
	public static final int TEXT_DIM = 0xFF9A9AA3;
	public static final int TEXT_MUTED = 0xFF6E6E76;
	public static final int TEXT_SECTION = 0xFF5C5C64;

	// Accents
	public static final int ACCENT = 0xFFE8A04A; // cursed amber (ours)
	public static final int ACCENT_SOFT = 0x55E8A04A;
	public static final int ONLINE = 0xFF5DCF7A;
	public static final int STAR = 0xFFE8C35A;
	public static final int DANGER = 0xFFE06A6A;
	public static final int TOGGLE_ON = 0xFFE8A04A; // accent when used
	public static final int TOGGLE_OFF = 0xFF3A3A42;
	public static final int TOGGLE_KNOB = 0xFFF2F2F5;

	// Geometry (gui-scaled px) — ClickGui-inspired proportions, slightly larger for readability
	public static final float BG_W = 500f;
	public static final float BG_H = 310f;
	public static final float RADIUS = 16f;
	public static final float SIDEBAR_W = 96f;
	public static final float HEADER_H = 36f;
	public static final float LIST_W = 132f;
	public static final float GAP = 6f;
	public static final float PAD = 8f;

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
