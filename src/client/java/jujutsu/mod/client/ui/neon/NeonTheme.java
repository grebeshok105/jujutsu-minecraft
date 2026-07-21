package jujutsu.mod.client.ui.neon;

import jujutsu.mod.client.ui.UiEase;

/**
 * Per-character neon palette. Surfaces are character-tinted mid-tones (not pure black)
 * so the dashboard reads as warm orange for Nobara / cool slate for None.
 */
public record NeonTheme(
        int accentArgb,
        int deepArgb,
        int panelTop,
        int panelBottom,
        int raised,
        int raisedBottom,
        int panelInset,
        int sidebarTop,
        int sidebarBottom,
        int scrimTop,
        int scrimBottom) {

    /** Nobara — warm amber wood / cursed-tool orange. */
    public static final NeonTheme NOBARA = new NeonTheme(
            0xFFE48A36,
            0xFF8B3F1C,
            0xF23C2A1E, // panel top  — readable warm brown
            0xF5302218, // panel bottom
            0xE64A3628, // raised
            0xE63E2C20,
            0xD92A1E16, // inset
            0xC038281C, // sidebar top
            0xA0281C14, // sidebar bottom
            0x991A100C, // scrim (not pure black)
            0xB0140C0A
    );

    /** None — cooler graphite / steel slate. */
    public static final NeonTheme NONE = new NeonTheme(
            0xFF7A8796,
            0xFF3A4450,
            0xF22A3038,
            0xF5222830,
            0xE6384048,
            0xE62E363E,
            0xD91E242C,
            0xC0283038,
            0xA01C2228,
            0x99101418,
            0xB00C1014
    );

    public int glow() { return withAlpha(accentArgb, 0xA0); }
    public int border() { return withAlpha(accentArgb, 0x55); }
    public int borderStrong() { return withAlpha(accentArgb, 0xB0); }
    public int fillAccentTop() { return withAlpha(accentArgb, 0x38); }
    public int fillAccentBottom() { return withAlpha(accentArgb, 0x18); }

    /** Soft accent wash for header/sidebar accents. */
    public int accentWash(float alpha) {
        return withAlpha(accentArgb, Math.round(UiEase.clamp01(alpha) * 255f));
    }

    public static int text() { return 0xFFFFF6EC; }
    public static int textMuted() { return 0xFFC8B8A8; }
    public static int textDim() { return 0xFF8A7A6C; }
    public static int textOnAccent() { return 0xFF1A0D02; }

    public NeonTheme lerp(NeonTheme other, float t) {
        t = UiEase.clamp01(t);
        return new NeonTheme(
                lerpColor(accentArgb, other.accentArgb, t),
                lerpColor(deepArgb, other.deepArgb, t),
                lerpColor(panelTop, other.panelTop, t),
                lerpColor(panelBottom, other.panelBottom, t),
                lerpColor(raised, other.raised, t),
                lerpColor(raisedBottom, other.raisedBottom, t),
                lerpColor(panelInset, other.panelInset, t),
                lerpColor(sidebarTop, other.sidebarTop, t),
                lerpColor(sidebarBottom, other.sidebarBottom, t),
                lerpColor(scrimTop, other.scrimTop, t),
                lerpColor(scrimBottom, other.scrimBottom, t)
        );
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpByte(from >>> 24, to >>> 24, t);
        int r = lerpByte(from >> 16, to >> 16, t);
        int g = lerpByte(from >> 8, to >> 8, t);
        int b = lerpByte(from, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpByte(int from, int to, float t) {
        return Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
    }
}
