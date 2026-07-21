package jujutsu.mod.client.ui.neon.widget;

import java.util.List;
import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
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

    private static final float ITEM_H = 20;

    public NeonDropdown(Component label, List<Component> options, int initial) {
        this.label = NeonFonts.wrap(label);
        this.options = options.stream().map(c -> (Component) NeonFonts.wrap(c)).toList();
        this.selectedIndex = Math.max(0, Math.min(initial, options.size() - 1));
        this.height = 20;
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
    public boolean contains(double mx, double my) {
        if (super.contains(mx, my)) return true;
        return open && isInPopup(mx, my);
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
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        int textY = (int) (ay + (height - 8) / 2f);
        g.drawString(ctx.font(), label, (int) ax, textY, NeonTheme.textMuted(), false);

        Component current = options.get(selectedIndex);
        int curW = ctx.font().width(current);
        g.drawString(ctx.font(), current, (int) (ax + width - 16 - curW - 6), textY, NeonTheme.text(), false);
        g.drawString(ctx.font(), NeonFonts.literal(open ? "\u25B2" : "\u25BC"), (int) (ax + width - 16), textY, t.accentArgb(), false);

        if (open) {
            // Draw popup last so later sibling rows cannot paint over it.
            ctx.deferOverlay(() -> drawPopup(ctx));
        }
    }

    private void drawPopup(NeonContext ctx) {
        GuiGraphics g = ctx.graphics();
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float py = ay + height + 2;
        float ph = options.size() * ITEM_H;

        // Opaque panel + accent border via GuiGraphics (always above SDF + sibling text).
        g.fill((int) ax, (int) py, (int) (ax + width), (int) (py + ph), 0xF51C1510);
        g.fill((int) ax, (int) py, (int) (ax + width), (int) py + 1, t.accentArgb());
        g.fill((int) ax, (int) (py + ph - 1), (int) (ax + width), (int) (py + ph), t.border());
        g.fill((int) ax, (int) py, (int) ax + 1, (int) (py + ph), t.border());
        g.fill((int) (ax + width - 1), (int) py, (int) (ax + width), (int) (py + ph), t.border());

        g.fill((int) (ax + 2), (int) (py + selectedIndex * ITEM_H + 1),
                (int) (ax + width - 2), (int) (py + selectedIndex * ITEM_H + ITEM_H - 1),
                applyAlpha(t.accentArgb(), 0.22f));
        for (int i = 0; i < options.size(); i++) {
            int color = i == selectedIndex ? NeonTheme.text() : NeonTheme.textMuted();
            g.drawString(ctx.font(), options.get(i), (int) (ax + 8), (int) (py + i * ITEM_H + 5), color, false);
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
        if (super.contains(mouseX, mouseY)) {
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
