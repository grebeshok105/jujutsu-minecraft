package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NeonSlider extends UiComponent {
    private final Component label;
    private float value;
    private boolean dragging;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float RAIL_H = 6;
    private static final float KNOB_R = 7;

    public NeonSlider(Component label, float initial) {
        this.label = label;
        this.value = UiEase.clamp01(initial);
        this.height = 30;
        this.width = 200;
    }

    public float value() { return value; }
    public void setValue(float v) { this.value = UiEase.clamp01(v); }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        if (dragging) {
            float railX = absX();
            float railW = width - 2 * KNOB_R;
            value = UiEase.clamp01((float) ((lastMouseX - railX - KNOB_R) / railW));
        }
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float railY = ay + 18;
        float railW = width;

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, railY, railW, RAIL_H)
                .radius(RAIL_H / 2f)
                .border(0, 0).glow(0, 0).highlight(0.1f)
                .fill(0x40303030, 0x40303030)
                .build());

        float fillW = value * (railW - 2 * KNOB_R) + KNOB_R;
        ctx.sdf().add(SdfShape.builder()
                .rect(ax, railY, fillW, RAIL_H)
                .radius(RAIL_H / 2f)
                .border(0, 0)
                .glow(4, applyAlpha(t.glow(), 0.3f))
                .highlight(0.4f)
                .fill(t.accentArgb(), t.deepArgb())
                .build());

        float knobX = ax + KNOB_R + value * (railW - 2 * KNOB_R) - KNOB_R;
        float knobY = railY + (RAIL_H - 2 * KNOB_R) / 2f;
        ctx.sdf().add(SdfShape.builder()
                .rect(knobX, knobY, KNOB_R * 2, KNOB_R * 2)
                .radius(KNOB_R)
                .border(1, t.borderStrong())
                .glow(dragging || hoveredThisFrame ? 8 : 0, applyAlpha(t.glow(), 0.5f))
                .highlight(0.6f)
                .fill(t.accentArgb(), t.accentArgb())
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        g.drawString(ctx.font(), label, (int) absX(), (int) absY(), NeonTheme.textMuted(), false);
        String pct = Math.round(value * 100) + "%";
        g.drawString(ctx.font(), pct, (int) (absX() + width - ctx.font().width(pct)), (int) absY(), NeonTheme.textDim(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            dragging = true;
            float railX = absX();
            float railW = width - 2 * KNOB_R;
            value = UiEase.clamp01((float) ((mouseX - railX - KNOB_R) / railW));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
