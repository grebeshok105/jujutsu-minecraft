package jujutsu.mod.client.gui.neon;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.UiContainer;

public final class PageContainer extends UiContainer {
    private NeonPage currentPage;
    private float switchAnim = 1f;
    private boolean switching;
    private long switchStartMillis;
    private static final long SWITCH_MS = 160;

    public void setPage(NeonPage page) {
        if (page == currentPage) return;
        children.clear();
        currentPage = page;
        add(page);
        page.setBounds(0f, 0f, width, height);
        page.setVisible(true);
        switching = true;
        switchStartMillis = System.currentTimeMillis();
        switchAnim = 0f;
        layout();
    }

    public NeonPage currentPage() { return currentPage; }

    @Override
    public void layout() {
        if (currentPage != null) {
            currentPage.setBounds(0f, 0f, width, height);
        }
        super.layout();
    }

    @Override
    public void tick(float deltaTicks) {
        if (switching) {
            float t = (System.currentTimeMillis() - switchStartMillis) / (float) SWITCH_MS;
            switchAnim = UiEase.outCubic(UiEase.clamp01(t));
            if (t >= 1f) switching = false;
        }
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible() || currentPage == null) return;
        NeonContext faded = new NeonContext(
                ctx.sdf(), ctx.graphics(), ctx.font(), ctx.theme(),
                ctx.mouseX(), ctx.mouseY(), ctx.deltaTicks(),
                ctx.openAnim() * switchAnim);
        super.renderSurface(faded);
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible() || currentPage == null) return;
        super.renderText(ctx);
    }
}
