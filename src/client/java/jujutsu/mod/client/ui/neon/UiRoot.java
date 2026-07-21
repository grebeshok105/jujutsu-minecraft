package jujutsu.mod.client.ui.neon;

import java.util.function.Consumer;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.render.SdfShape;

public final class UiRoot extends UiContainer {
    private float headerHeight = 40f;

    private float windowX, windowY, windowW, windowH;
    private boolean dragging;
    private float dragOffX, dragOffY;
    private NeonTheme theme;
    private final Runnable closeAction;
    private Consumer<NeonContext> chromeRenderer;

    public UiRoot(NeonTheme theme, Runnable closeAction) {
        this.theme = theme;
        this.closeAction = closeAction;
    }

    /** Chrome (header/sidebar surfaces) is drawn after the window panel, before the children. */
    public void setChromeRenderer(Consumer<NeonContext> chromeRenderer) {
        this.chromeRenderer = chromeRenderer;
    }

    public void setHeaderHeight(float headerHeight) {
        this.headerHeight = headerHeight;
    }

    public float headerHeight() {
        return headerHeight;
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
    public void setTheme(NeonTheme t) { this.theme = t; }

    public boolean isInHeader(double mx, double my) {
        return mx >= windowX && mx < windowX + windowW && my >= windowY && my < windowY + headerHeight;
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
        windowY = Math.max(0, Math.min(windowY, screenH - headerHeight));
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

        float scale = 0.96f + 0.04f * ctx.openAnim();
        float cx = windowX + windowW / 2f;
        float cy = windowY + windowH / 2f;
        float sw = windowW * scale;
        float sh = windowH * scale;
        float sx = cx - sw / 2f;
        float sy = cy - sh / 2f;

        // Character-tinted panel: stronger accent border/glow so theme is obvious.
        sdf.add(SdfShape.builder().rect(sx, sy, sw, sh)
                .radius(10)
                .border(1.5f, t.borderStrong())
                .glow(18, t.glow())
                .highlight(0.85f)
                .fill(t.panelTop(), t.panelBottom())
                .build());

        if (chromeRenderer != null) {
            chromeRenderer.accept(ctx);
        }

        super.renderSurface(ctx);
    }
}
