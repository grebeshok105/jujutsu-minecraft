package jujutsu.mod.client.ui.neon;

import jujutsu.mod.client.ui.UiEase;
import net.minecraft.client.gui.GuiGraphics;

public abstract class UiComponent {
    protected float x, y, width, height;
    protected UiContainer parent;
    protected boolean visible = true;
    protected float hover;
    protected float press;
    protected boolean pressed;

    public float x() { return x; }
    public float y() { return y; }
    public float width() { return width; }
    public float height() { return height; }

    public void setBounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
    }

    public void setParent(UiContainer parent) { this.parent = parent; }
    public UiContainer parent() { return parent; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }

    public float absX() {
        float ax = x;
        UiContainer p = parent;
        while (p != null) { ax += p.x(); p = p.parent(); }
        return ax;
    }

    public float absY() {
        float ay = y;
        UiContainer p = parent;
        while (p != null) { ay += p.y(); p = p.parent(); }
        return ay;
    }

    public void tick(float deltaTicks) {
        animateHover(deltaTicks);
    }

    protected void animateHover(float deltaTicks) {
        hover = UiEase.approach(hover, isHovered() ? 1f : 0f, 0.35f, deltaTicks);
        press = UiEase.approach(press, pressed && isHovered() ? 1f : 0f, 0.5f, deltaTicks);
    }

    public void renderSurface(NeonContext ctx) {}
    public void renderText(NeonContext ctx) {}

    public boolean mouseClicked(double mouseX, double mouseY, int button) { return false; }
    public boolean mouseReleased(double mouseX, double mouseY, int button) { return false; }
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    public boolean contains(double mx, double my) {
        float ax = absX(), ay = absY();
        return mx >= ax && mx < ax + width && my >= ay && my < ay + height;
    }

    protected boolean isHovered() { return false; }
}
