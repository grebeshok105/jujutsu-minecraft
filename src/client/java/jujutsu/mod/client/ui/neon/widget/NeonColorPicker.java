package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NeonColorPicker extends UiComponent {
    private final Component label;
    private int colorArgb;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float SWATCH_SIZE = 18;

    public NeonColorPicker(Component label, int initialColor) {
        this.label = NeonFonts.wrap(label);
        this.colorArgb = initialColor | 0xFF000000;
        this.height = 24;
        this.width = 200;
    }

    public int color() { return colorArgb; }
    public void setColor(int argb) { this.colorArgb = argb | 0xFF000000; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float swatchX = ax + width - SWATCH_SIZE - 4;
        float swatchY = ay + (height - SWATCH_SIZE) / 2f;

        ctx.sdf().add(SdfShape.builder()
                .rect(swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE)
                .radius(4)
                .border(1, hoveredThisFrame ? t.borderStrong() : t.border())
                .glow(hoveredThisFrame ? 6 : 0, applyAlpha(t.glow(), 0.3f))
                .highlight(0.3f)
                .fill(colorArgb, colorArgb)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        NeonFonts.drawVCenter(g, ctx.font(), label, absX(), absY(), height, NeonTheme.textMuted());

        String hex = String.format("#%06X", colorArgb & 0x00FFFFFF);
        int hexW = NeonFonts.width(ctx.font(), hex);
        float hexX = absX() + width - SWATCH_SIZE - 4 - hexW - 8;
        NeonFonts.drawVCenter(g, ctx.font(), hex, hexX, absY(), height, NeonTheme.textDim());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            // Shell only: cycle through a few preset colors
            int[] presets = {0xFFE48A36, 0xFFDC2743, 0xFF4ADE80, 0xFF60A5FA, 0xFFA78BFA, 0xFF505760};
            int current = -1;
            for (int i = 0; i < presets.length; i++) {
                if ((presets[i] | 0xFF000000) == colorArgb) { current = i; break; }
            }
            colorArgb = presets[(current + 1) % presets.length] | 0xFF000000;
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
