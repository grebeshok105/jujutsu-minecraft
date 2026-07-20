package jujutsu.mod.client.ui.neon;

import jujutsu.mod.client.ui.UiEase;

public record NeonTheme(int accentArgb, int deepArgb) {

    public static final NeonTheme NOBARA = new NeonTheme(0xFFE48A36, 0xFF8B3F1C);
    public static final NeonTheme NONE = new NeonTheme(0xFF505760, 0xFF181C24);

    public int glow() { return withAlpha(accentArgb, 0x8C); }
    public int border() { return withAlpha(accentArgb, 0x38); }
    public int borderStrong() { return withAlpha(accentArgb, 0x8C); }
    public int fillAccentTop() { return withAlpha(accentArgb, 0x24); }
    public int fillAccentBottom() { return withAlpha(accentArgb, 0x0A); }

    public int panelTop() { return 0xEB17110F; }
    public int panelBottom() { return 0xF0110C0A; }
    public int raised() { return 0xD9211914; }
    public int raisedBottom() { return 0xD9181210; }
    public int scrimTop() { return 0xB8090605; }
    public int scrimBottom() { return 0xC7090605; }
    public int sidebarTop() { return 0x80131009; }
    public int sidebarBottom() { return 0x4D0F0A08; }

    public static int text() { return 0xFFF4EFE8; }
    public static int textMuted() { return 0xFFC8C0B8; }
    public static int textDim() { return 0xFF635850; }
    public static int textOnAccent() { return 0xFF1A0410; }

    public NeonTheme lerp(NeonTheme other, float t) {
        t = UiEase.clamp01(t);
        return new NeonTheme(lerpColor(accentArgb, other.accentArgb, t), lerpColor(deepArgb, other.deepArgb, t));
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
