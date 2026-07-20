package jujutsu.mod.client.gui;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiRoot;
import jujutsu.mod.client.ui.neon.render.NeonBlur;
import jujutsu.mod.client.ui.neon.render.SdfRenderer;
import jujutsu.mod.client.ui.neon.widget.NeonButton;
import jujutsu.mod.client.ui.neon.widget.NeonLabel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class NeonDashboardScreen extends Screen {
    private static final long OPEN_MS = 260;
    private static final long CLOSE_MS = 200;

    private final SdfRenderer sdf = new SdfRenderer();
    private UiRoot root;
    private NeonTheme theme = NeonTheme.NOBARA;

    private float openAnim;
    private boolean closing;
    private boolean disposed;
    private long openStartMillis;
    private long closeStartMillis;
    private long lastFrameNanos;

    public NeonDashboardScreen() {
        super(Component.empty());
        openStartMillis = System.currentTimeMillis();
        lastFrameNanos = System.nanoTime();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        if (root == null) {
            root = buildRoot();
        }
        layoutWindow();
        root.layout();
    }

    @Override
    public void resize(net.minecraft.client.Minecraft mc, int w, int h) {
        super.resize(mc, w, h);
        layoutWindow();
        root.layout();
    }

    private void layoutWindow() {
        float ww = Math.min(660, width - 40);
        float wh = Math.min(440, height - 40);
        float wx = (width - ww) / 2f;
        float wy = (height - wh) / 2f;
        root.setWindow(wx, wy, ww, wh);
    }

    private UiRoot buildRoot() {
        UiRoot r = new UiRoot(theme, this::animateClose);

        NeonLabel title = new NeonLabel(Component.literal("JUJUTSU // DASHBOARD"), NeonTheme.text(), false);
        title.setBounds(14, 15, 200, 10);
        r.add(title);

        NeonButton closeBtn = new NeonButton(Component.literal("\u2715"), 24, 24, false, this::animateClose);
        closeBtn.setBounds(0, 0, 24, 24);
        r.add(closeBtn);

        NeonLabel placeholder = new NeonLabel(Component.literal("Stage 3: empty shell"), NeonTheme.textDim(), false);
        placeholder.setBounds(0, 0, 200, 10);
        r.add(placeholder);

        return r;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        float deltaTicks = (now - lastFrameNanos) / 50_000_000f;
        deltaTicks = Math.max(0.05f, Math.min(deltaTicks, 3f));
        lastFrameNanos = now;

        updateAnimation();

        root.drag(mouseX, mouseY, width, height);
        updateCloseButtonPosition();
        updatePlaceholderPosition();

        for (var child : root.children()) {
            if (child instanceof NeonButton btn) btn.updateMouse(mouseX, mouseY);
        }
        root.tick(deltaTicks);

        float anim = UiEase.outCubic(openAnim);
        NeonContext ctx = new NeonContext(sdf, g, font, theme, mouseX, mouseY, deltaTicks, anim);

        sdf.begin();
        root.renderSurface(ctx);
        sdf.flush();

        root.renderText(ctx);
    }

    private void updateCloseButtonPosition() {
        for (var child : root.children()) {
            if (child instanceof NeonButton) {
                child.setBounds(root.windowW() - 34, 8, 24, 24);
                break;
            }
        }
    }

    private void updatePlaceholderPosition() {
        boolean foundTitle = false;
        for (var child : root.children()) {
            if (child instanceof NeonLabel label) {
                if (!foundTitle) { foundTitle = true; continue; }
                label.setBounds(root.windowW() / 2f - 60, root.windowH() / 2f, 200, 10);
                break;
            }
        }
    }

    private void updateAnimation() {
        long now = System.currentTimeMillis();
        if (closing) {
            float t = (now - closeStartMillis) / (float) CLOSE_MS;
            openAnim = 1f - UiEase.clamp01(t);
            if (t >= 1f && !disposed) {
                disposed = true;
                sdf.close();
                super.onClose();
            }
        } else {
            float t = (now - openStartMillis) / (float) OPEN_MS;
            openAnim = UiEase.clamp01(t);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        NeonBlur.apply();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (root.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && root.isInHeader(mouseX, mouseY)) {
            root.startDrag(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        root.endDrag();
        return root.mouseReleased(mouseX, mouseY, button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (root.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        animateClose();
    }

    private void animateClose() {
        if (closing) return;
        closing = true;
        closeStartMillis = System.currentTimeMillis();
    }

    @Override
    public void removed() {
        if (!disposed) {
            disposed = true;
            sdf.close();
        }
        super.removed();
    }
}
