package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class SidebarItem extends UiComponent {
    private final Component label;
    private final String glyph;
    private final Runnable onSelect;
    private boolean selected;
    private float selectAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public SidebarItem(String glyph, Component label, Runnable onSelect) {
        this.glyph = glyph;
        this.label = label;
        this.onSelect = onSelect;
        this.height = 34;
    }

    public void setSelected(boolean s) { this.selected = s; }
    public boolean isSelected() { return selected; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        float target = selected ? 1f : (hoveredThisFrame ? 0.4f : 0f);
        selectAnim = UiEase.approach(selectAnim, target, 0.3f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible() || selectAnim < 0.01f) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        int borderA = applyAlpha(t.borderStrong(), 0.3f * selectAnim);
        int glowA = applyAlpha(t.glow(), 0.55f * selectAnim);
        int fillA = applyAlpha(t.accentArgb(), 0.14f * selectAnim);

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(6)
                .border(1, borderA)
                .glow(10 * selectAnim, glowA)
                .highlight(0.5f * selectAnim)
                .fill(fillA, applyAlpha(t.accentArgb(), 0.04f * selectAnim))
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();
        int textColor = selected ? NeonTheme.text() : NeonTheme.textDim();
        g.drawString(ctx.font(), glyph, (int) (ax + 10), (int) (ay + 12), textColor, false);
        g.drawString(ctx.font(), label, (int) (ax + 28), (int) (ay + 12), textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            onSelect.run();
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
