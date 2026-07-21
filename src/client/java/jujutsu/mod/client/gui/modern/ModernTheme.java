package jujutsu.mod.client.gui.modern;

/**
 * Exact tokens from Rich-Modern clickgui sources (not guessed):
 * <ul>
 *   <li>{@code BackgroundComponent.BG_WIDTH/HEIGHT = 400/250}</li>
 *   <li>{@code BackgroundRenderer} gradient + category panel</li>
 *   <li>{@code ModuleListRenderer} row 22, radius 6/5</li>
 *   <li>{@code SettingsPanelRenderer} radius 7</li>
 *   <li>{@code CategoryRenderer} 15px step, size 6</li>
 *   <li>{@code HeaderRenderer} header bar + search 70×15 @ 315</li>
 * </ul>
 */
public final class ModernTheme {
	private ModernTheme() {}

	// BackgroundComponent
	public static final float BG_W = 400f;
	public static final float BG_H = 250f;
	public static final float SHELL_RADIUS = 15f;

	// Layout from ClickGui + HeaderRenderer
	public static final float SIDEBAR_INSET = 7.5f;
	public static final float SIDEBAR_W = 80f; // category panel width in BackgroundRenderer
	public static final float HEADER_X = 92f;
	public static final float HEADER_Y = 7.5f;
	public static final float HEADER_H = 25f;
	public static final float HEADER_RADIUS = 8f;
	public static final float LIST_X = 92f;
	public static final float LIST_Y = 38f;
	public static final float LIST_W = 120f;
	public static final float SETTINGS_X = 218f;
	public static final float SETTINGS_Y = 38f;
	public static final float SETTINGS_W = 172f;
	public static final float CONTENT_H = BG_H - 46f; // ClickGui: BG_HEIGHT - 46

	// ModuleListRenderer
	public static final float MODULE_ITEM_H = 22f;
	public static final float MODULE_GAP = 2f;
	public static final float MODULE_LIST_RADIUS = 6f;
	public static final float MODULE_ITEM_RADIUS = 5f;

	// SettingsPanelRenderer
	public static final float SETTINGS_RADIUS = 7f;
	public static final float SETTING_H = 16f;
	public static final float SETTING_GAP = 2f;

	// CategoryRenderer
	public static final float CAT_STEP = 15f;
	public static final float CAT_TEXT = 6f;
	public static final float CAT_ICON = 6f;
	public static final float CAT_BALL = 3f;
	public static final float CAT_MAX_OFFSET = 5f;

	// Colors as AARRGGBB from new Color(r,g,b,a).getRGB()
	public static int argb(int r, int g, int b, int a) {
		return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
	}

	public static int withAlphaMul(int argb, float mul) {
		int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(mul));
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	public static float clamp01(float t) {
		return t < 0f ? 0f : Math.min(1f, t);
	}

	public static float approach(float cur, float target, float speed, float dt) {
		float diff = target - cur;
		if (Math.abs(diff) < 0.001f) {
			return target;
		}
		return cur + diff * speed * dt;
	}
}
