package jujutsu.mod.client.ui.neon;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.render.SdfShape;

/** Soft warm halo under Apple-emoji PNG icons (SDF outer glow, transparent core). */
public final class EmojiGlow {
    private EmojiGlow() {}

    /**
     * @param cx center X of the icon in screen space
     * @param cy center Y of the icon in screen space
     * @param iconSize drawn icon size in px
     * @param accentArgb theme/card accent (ARGB)
     * @param strength 0..1 intensity multiplier
     */
    public static void add(NeonContext ctx, float cx, float cy, float iconSize, int accentArgb, float strength) {
        strength = UiEase.clamp01(strength);
        if (strength < 0.01f) return;

        float pad = iconSize * 0.35f;
        float size = iconSize + pad * 2f;
        float x = cx - size / 2f;
        float y = cy - size / 2f;
        float radius = size / 2f;

        // Outer soft bloom (accent-tinted).
        ctx.sdf().add(SdfShape.builder()
                .rect(x, y, size, size)
                .radius(radius)
                .border(0, 0)
                .glow(14f * strength, applyAlpha(accentArgb, 0.55f * strength))
                .fill(applyAlpha(accentArgb, 0.14f * strength), applyAlpha(accentArgb, 0.02f * strength))
                .highlight(0f)
                .build());

        // Inner warm ring so the halo reads around the emoji, not only behind it.
        float inner = iconSize * 0.95f;
        ctx.sdf().add(SdfShape.builder()
                .rect(cx - inner / 2f, cy - inner / 2f, inner, inner)
                .radius(inner / 2f)
                .border(1.2f, applyAlpha(0xFFFFE0B0, 0.35f * strength))
                .glow(6f * strength, applyAlpha(0xFFFFC878, 0.40f * strength))
                .fill(0x00000000, 0x00000000)
                .highlight(0f)
                .build());
    }

    private static int applyAlpha(int argb, float alpha) {
        int baseA = (argb >>> 24) & 0xFF;
        if (baseA == 0) baseA = 255;
        int a = Math.round((baseA / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
