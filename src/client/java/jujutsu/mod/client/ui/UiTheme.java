package jujutsu.mod.client.ui;

/**
 * Single source of truth for the custom UI look: character accents on dark cursed glass. Keeping
 * every color here means every screen and widget stays visually consistent.
 */
public final class UiTheme {
	private UiTheme() {}

	// Surfaces.
	public static final int SCRIM = 0xC0343539;
	public static final int PANEL = 0xF228292E;
	public static final int PANEL_RAISED = 0xF232333A;
	public static final int PANEL_INSET = 0xF01B1C22;
	public static final int BORDER = 0x334D5662;
	public static final int BORDER_STRONG = 0x66727C8A;

	// Accents.
	public static final int ACCENT = 0xFFFF54C8;
	public static final int ACCENT_DEEP = 0xFFB01E4B;
	public static final int ACCENT_RGB = 0x00FF54C8;
	public static final int OXBLOOD = 0xFF7A1030;
	public static final int NOBARA_ACCENT = 0xFFE48A36;
	public static final int NOBARA_ACCENT_DEEP = 0xFF8B3F1C;
	public static final int NOBARA_ACCENT_RGB = 0x00E48A36;
	public static final int NONE_ACCENT = 0xFF505760;
	public static final int NONE_ACCENT_DEEP = 0xFF181C24;
	public static final int NONE_ACCENT_RGB = 0x00505760;

	// Text.
	public static final int TEXT = 0xFFF4EFE8;
	public static final int TEXT_MUTED = 0xFFC8C0B8;
	public static final int TEXT_DIM = 0xFF8A8582;
	public static final int TEXT_ON_ACCENT = 0xFF1A0410;

	// Timing (ms).
	public static final int OPEN_MS = 260;
	public static final int HOVER_MS = 140;
}
