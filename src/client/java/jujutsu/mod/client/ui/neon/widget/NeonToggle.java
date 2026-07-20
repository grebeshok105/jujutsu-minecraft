package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NeonToggle extends UiComponent {
    private final Component label;
    private boolean state;
    private float knobAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float TRACK_W = 36;
    private static final float TRACK_H = 18;
    private static final float KNOB_R = 7;

    public NeonToggle(Component label, boolean initial) {
        this.label = label;
        this.state = initial;
        this.knobAnim = initial ? 1f : 0f;
        this.height = 24;
        this.width = 200;
    }

    public boolean state() { return state; }
    public void setState(boolean s) { this.state = s; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        knobAnim = UiEase.approach(knobAnim, state ? 1f : 0f, 0.35f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float trackX = ax + width - TRACK_W - 4;
        float trackY = ay + (height - TRACK_H) / 2f;

        int trackFill = state ? applyAlpha(t.accentArgb(), 0.5f + 0.2f * hover) : 0x40303030;
        int trackBorder = state ? t.borderStrong() : 0x30505050;

        ctx.sdf().add(SdfShape.builder()
                .rect(trackX, trackY, TRACK_W, TRACK_H)
                .radius(TRACK_H / 2f)
                .border(1, trackBorder)
                .glow(state ? 6 : 0, applyAlpha(t.glow(), 0.3f))
                .highlight(0.3f)
                .fill(trackFill, trackFill)
                .build());

        float knobX = trackX + 3 + knobAnim * (TRACK_W - 2 * KNOB_R - 6);
        float knobY = trackY + (TRACK_H - 2 * KNOB_R) / 2f;
        int knobColor = state ? t.accentArgb() : 0xFF808080;
        ctx.sdf().add(SdfShape.builder()
                .rect(knobX, knobY, KNOB_R * 2, KNOB_R * 2)
                .radius(KNOB_R)
                .border(0, 0)
                .glow(0, 0)
                .highlight(0.5f)
                .fill(knobColor, knobColor)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        g.drawString(ctx.font(), label, (int) absX(), (int) (absY() + 7), NeonTheme.textMuted(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            state = !state;
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
