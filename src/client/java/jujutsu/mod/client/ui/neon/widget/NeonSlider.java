package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Mockup slider: fixed-width rail on the right, warm-white handle with accent border, absolute value. */
public final class NeonSlider extends UiComponent {
    private final Component label;
    private final float min;
    private final float max;
    private float value;
    private boolean dragging;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float RAIL_W = 130;
    private static final float RAIL_H = 3;
    private static final float KNOB_R = 6;

    public NeonSlider(Component label, float min, float max, float initial) {
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = UiEase.clamp01((initial - min) / Math.max(1e-6f, max - min));
        this.height = 22;
        this.width = 200;
    }

    public float value() { return min + value * (max - min); }
    public void setValue(float v) { this.value = UiEase.clamp01((v - min) / Math.max(1e-6f, max - min)); }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    private float railX() { return absX() + width - RAIL_W; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        if (dragging) {
            value = UiEase.clamp01((float) ((lastMouseX - railX() - KNOB_R) / (RAIL_W - 2 * KNOB_R)));
        }
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ay = absY();
        float rx = railX();
        // Rail under the value label, within the compact 22px control height.
        float railY = ay + 14;

        ctx.sdf().add(SdfShape.builder()
                .rect(rx, railY, RAIL_W, RAIL_H)
                .radius(RAIL_H / 2f)
                .border(0, 0).glow(0, 0).highlight(0.1f)
                .fill(0x99403026, 0x99403026)
                .build());

        float fillW = value * (RAIL_W - 2 * KNOB_R) + KNOB_R;
        ctx.sdf().add(SdfShape.builder()
                .rect(rx, railY, fillW, RAIL_H)
                .radius(RAIL_H / 2f)
                .border(0, 0)
                .glow(4, applyAlpha(t.glow(), 0.35f))
                .highlight(0.4f)
                .fill(t.deepArgb() | 0xFF000000, t.accentArgb() | 0xFF000000)
                .build());

        float knobX = rx + KNOB_R + value * (RAIL_W - 2 * KNOB_R) - KNOB_R;
        float knobY = railY + (RAIL_H - 2 * KNOB_R) / 2f;
        ctx.sdf().add(SdfShape.builder()
                .rect(knobX, knobY, KNOB_R * 2, KNOB_R * 2)
                .radius(KNOB_R)
                .border(2, t.accentArgb())
                .glow(dragging || hoveredThisFrame ? 8 : 4, applyAlpha(t.glow(), 0.5f))
                .highlight(0.6f)
                .fill(0xFFFFE9D2, 0xFFFFE9D2)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        NeonTheme t = ctx.theme();
        g.drawString(ctx.font(), label, (int) absX(), (int) (absY() + 2), NeonTheme.textMuted(), false);
        String val = String.valueOf(Math.round(value()));
        g.drawString(ctx.font(), val, (int) (railX() + RAIL_W - ctx.font().width(val)), (int) (absY() + 2), t.accentArgb(), false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            dragging = true;
            value = UiEase.clamp01((float) ((mouseX - railX() - KNOB_R) / (RAIL_W - 2 * KNOB_R)));
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
