package jujutsu.mod.client.ui.neon.layout;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiContainer;
import net.minecraft.client.gui.GuiGraphics;

public final class ScrollContainer extends UiContainer {
    private float scrollOffset;
    private float targetOffset;
    private float contentHeight;

    public void setContentHeight(float h) { this.contentHeight = h; }
    public float contentHeight() { return contentHeight; }
    public float scrollOffset() { return scrollOffset; }

    public float maxScroll() {
        return Math.max(0, contentHeight - height);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!contains(mouseX, mouseY)) return false;
        targetOffset = UiEase.clamp01((float) (targetOffset - delta * 20) / Math.max(1, maxScroll())) * maxScroll();
        return true;
    }

    @Override
    public void tick(float deltaTicks) {
        scrollOffset = UiEase.approach(scrollOffset, targetOffset, 0.3f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            float childAbsY = child.absY() - scrollOffset;
            if (childAbsY + child.height() < absY() || childAbsY > absY() + height) continue;
            child.renderSurface(ctx);
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        int x0 = (int) absX();
        int y0 = (int) absY();
        int x1 = (int) (absX() + width);
        int y1 = (int) (absY() + height);
        g.enableScissor(x0, y0, x1, y1);
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            child.renderText(ctx);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY)) return false;
        return super.mouseClicked(mouseX, mouseY + scrollOffset, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY + scrollOffset, button);
    }

    @Override
    public boolean contains(double mx, double my) {
        float ax = absX(), ay = absY();
        return mx >= ax && mx < ax + width && my >= ay && my < ay + height;
    }
}
