package jujutsu.mod.client.ui.neon;

import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;

public final class UiRoot extends UiContainer {
    public static final float HEADER_HEIGHT = 40f;

    private float windowX, windowY, windowW, windowH;
    private boolean dragging;
    private float dragOffX, dragOffY;
    private final NeonTheme theme;
    private final Runnable closeAction;

    public UiRoot(NeonTheme theme, Runnable closeAction) {
        this.theme = theme;
        this.closeAction = closeAction;
    }

    public void setWindow(float x, float y, float w, float h) {
        this.windowX = x;
        this.windowY = y;
        this.windowW = w;
        this.windowH = h;
        setBounds(x, y, w, h);
    }

    public float windowX() { return windowX; }
    public float windowY() { return windowY; }
    public float windowW() { return windowW; }
    public float windowH() { return windowH; }
    public NeonTheme theme() { return theme; }

    public boolean isInHeader(double mx, double my) {
        return mx >= windowX && mx < windowX + windowW && my >= windowY && my < windowY + HEADER_HEIGHT;
    }

    public void startDrag(double mx, double my) {
        dragging = true;
        dragOffX = (float) (mx - windowX);
        dragOffY = (float) (my - windowY);
    }

    public void drag(float mx, float my, int screenW, int screenH) {
        if (!dragging) return;
        windowX = mx - dragOffX;
        windowY = my - dragOffY;
        windowX = Math.max(0, Math.min(windowX, screenW - windowW));
        windowY = Math.max(0, Math.min(windowY, screenH - HEADER_HEIGHT));
        setBounds(windowX, windowY, windowW, windowH);
        layout();
    }

    public void endDrag() { dragging = false; }
    public boolean isDragging() { return dragging; }

    public void requestClose() { closeAction.run(); }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!visible) return;
        SdfRenderer sdf = ctx.sdf();
        NeonTheme t = ctx.theme();

        sdf.add(SdfShape.builder().rect(0, 0, ctx.graphics().guiWidth(), ctx.graphics().guiHeight())
                .radius(0).border(0, 0).glow(0, 0)
                .fill(t.scrimTop(), t.scrimBottom()).build());

        sdf.add(SdfShape.builder().rect(windowX, windowY, windowW, windowH)
                .radius(10).border(1, t.border()).glow(12, t.glow()).highlight(1f)
                .fill(t.panelTop(), t.panelBottom()).build());

        super.renderSurface(ctx);
    }
}
