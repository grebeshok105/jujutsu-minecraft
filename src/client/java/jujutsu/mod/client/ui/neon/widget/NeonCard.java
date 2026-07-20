package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class NeonCard extends UiComponent {
    private final Component name;
    private final Component role;
    private final ResourceLocation portrait;
    private final int accentRgb;
    private final Runnable onSelect;
    private boolean selected;
    private float selectAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public NeonCard(Component name, Component role, ResourceLocation portrait, int accentRgb, Runnable onSelect) {
        this.name = name;
        this.role = role;
        this.portrait = portrait;
        this.accentRgb = accentRgb;
        this.onSelect = onSelect;
        this.width = 140;
        this.height = 150;
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
        float target = selected ? 1f : (hoveredThisFrame ? 0.35f : 0f);
        selectAnim = UiEase.approach(selectAnim, target, 0.3f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        float ax = absX(), ay = absY();
        int borderA = applyAlpha(accentRgb, 0.2f + 0.6f * selectAnim);
        int glowA = applyAlpha(accentRgb, 0.55f * selectAnim);

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(8)
                .border(1, borderA)
                .glow(14 * selectAnim, glowA)
                .highlight(0.2f + 0.6f * selectAnim)
                .fill(0xD9211914, 0xD9181210)
                .build());

        if (portrait != null) {
            float headSize = 48;
            float headX = ax + (width - headSize) / 2f;
            float headY = ay + 16;
            ctx.sdf().add(SdfShape.builder()
                    .rect(headX, headY, headSize, headSize)
                    .radius(6)
                    .border(1, applyAlpha(accentRgb, 0.3f + 0.3f * selectAnim))
                    .glow(0, 0).highlight(0.2f)
                    .fill(0xCC090A0E, 0xCC090A0E)
                    .build());
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        float ax = absX(), ay = absY();

        int nameW = ctx.font().width(name);
        g.drawString(ctx.font(), name, (int) (ax + (width - nameW) / 2f), (int) (ay + 74), NeonTheme.text(), false);

        int roleW = ctx.font().width(role);
        g.drawString(ctx.font(), role, (int) (ax + (width - roleW) / 2f), (int) (ay + 88), NeonTheme.textDim(), false);

        if (selected) {
            g.drawString(ctx.font(), "\u2713", (int) (ax + width - 18), (int) (ay + 6), 0xFF4ADE80, false);
        }
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
