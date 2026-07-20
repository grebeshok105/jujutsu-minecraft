package jujutsu.mod.client.ui.neon.widget;

import java.util.List;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NeonDropdown extends UiComponent {
    private final Component label;
    private final List<Component> options;
    private int selectedIndex;
    private boolean open;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float ITEM_H = 22;

    public NeonDropdown(Component label, List<Component> options, int initial) {
        this.label = label;
        this.options = List.copyOf(options);
        this.selectedIndex = Math.max(0, Math.min(initial, options.size() - 1));
        this.height = 24;
        this.width = 200;
    }

    public int selectedIndex() { return selectedIndex; }
    public boolean isOpen() { return open; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY) || (open && isInPopup(lastMouseX, lastMouseY));
        super.tick(deltaTicks);
    }

    private boolean isInPopup(double mx, double my) {
        float ax = absX(), ay = absY() + height + 2;
        float ph = options.size() * ITEM_H;
        return mx >= ax && mx < ax + width && my >= ay && my < ay + ph;
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(6)
                .border(1, open ? t.borderStrong() : t.border())
                .glow(open ? 6 : 0, applyAlpha(t.glow(), 0.3f))
                .highlight(0.2f)
                .fill(t.raised(), t.raisedBottom())
                .build());

        if (open) {
            float py = ay + height + 2;
            float ph = options.size() * ITEM_H;
            ctx.sdf().add(SdfShape.builder()
                    .rect(ax, py, width, ph)
                    .radius(6)
                    .border(1, t.border())
                    .glow(8, applyAlpha(t.glow(), 0.25f))
                    .highlight(0.1f)
                    .fill(0xF51A1410, 0xF5141008)
                    .build());

            for (int i = 0; i < options.size(); i++) {
                if (i == selectedIndex) {
                    ctx.sdf().add(SdfShape.builder()
                            .rect(ax + 2, py + i * ITEM_H + 1, width - 4, ITEM_H - 2)
                            .radius(4)
                            .border(0, 0).glow(0, 0).highlight(0.3f)
                            .fill(applyAlpha(t.accentArgb(), 0.15f), applyAlpha(t.accentArgb(), 0.08f))
                            .build());
                }
            }
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();
        g.drawString(ctx.font(), label, (int) ax, (int) (ay - 12), NeonTheme.textDim(), false);
        g.drawString(ctx.font(), options.get(selectedIndex), (int) (ax + 8), (int) (ay + 7), NeonTheme.text(), false);
        g.drawString(ctx.font(), open ? "\u25B2" : "\u25BC", (int) (ax + width - 16), (int) (ay + 7), NeonTheme.textDim(), false);

        if (open) {
            float py = ay + height + 2;
            float ph = options.size() * ITEM_H;
            g.fill((int) ax, (int) py, (int) (ax + width), (int) (py + ph), 0xF51A1410);
            for (int i = 0; i < options.size(); i++) {
                int color = i == selectedIndex ? NeonTheme.text() : NeonTheme.textMuted();
                g.drawString(ctx.font(), options.get(i), (int) (ax + 8), (int) (py + i * ITEM_H + 6), color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (open && isInPopup(mouseX, mouseY)) {
            float py = absY() + height + 2;
            int idx = (int) ((mouseY - py) / ITEM_H);
            if (idx >= 0 && idx < options.size()) {
                selectedIndex = idx;
            }
            open = false;
            return true;
        }
        if (contains(mouseX, mouseY)) {
            open = !open;
            return true;
        }
        if (open) {
            open = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (open && keyCode == 256) {
            open = false;
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
