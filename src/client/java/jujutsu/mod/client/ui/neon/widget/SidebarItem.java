package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SidebarItem extends UiComponent {
    private final Component label;
    private final ResourceLocation icon;
    private final Runnable onSelect;
    private boolean selected;
    private float selectAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public SidebarItem(ResourceLocation icon, Component label, Runnable onSelect) {
        this.icon = icon;
        this.label = NeonFonts.wrap(label);
        this.onSelect = onSelect;
        this.height = 26;
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
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();

        // Soft emoji glow always present; stronger when selected.
        float iconGlow = 0.22f + 0.35f * selectAnim;
        ctx.sdf().add(SdfShape.builder()
                .rect(ax + 6, ay + 5, 16, 16)
                .radius(8)
                .border(0, 0)
                .glow(8 + 4 * selectAnim, applyAlpha(t.glow(), iconGlow))
                .fill(applyAlpha(t.accentArgb(), 0.08f + 0.10f * selectAnim), applyAlpha(t.accentArgb(), 0.02f))
                .highlight(0f)
                .build());

        if (selectAnim < 0.01f) return;

        int borderA = applyAlpha(t.borderStrong(), 0.35f * selectAnim);
        int glowA = applyAlpha(t.glow(), 0.5f * selectAnim);
        int fillA = applyAlpha(t.accentArgb(), 0.14f * selectAnim);

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(6)
                .border(1, borderA)
                .glow(10 * selectAnim, glowA)
                .highlight(0.4f * selectAnim)
                .fill(fillA, applyAlpha(t.accentArgb(), 0.03f * selectAnim))
                .build());

        float barH = height * 0.6f * selectAnim;
        float barY = ay + (height - barH) / 2f;
        ctx.sdf().add(SdfShape.builder()
                .rect(ax - 1, barY, 2, barH)
                .radius(1).border(0, 0)
                .glow(6 * selectAnim, applyAlpha(t.glow(), 0.6f * selectAnim))
                .highlight(0f)
                .fill(t.accentArgb(), t.accentArgb())
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();
        int textColor = selected ? NeonTheme.text() : NeonTheme.textMuted();
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, (int) (ax + 7), (int) (ay + 6),
                    0f, 0f, 14, 14, 96, 96, 96, 96);
        }
        g.drawString(ctx.font(), label, (int) (ax + 26), (int) (ay + 9), textColor, false);
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
