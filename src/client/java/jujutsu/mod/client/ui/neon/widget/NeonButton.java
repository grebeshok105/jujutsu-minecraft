package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public final class NeonButton extends UiComponent {
    private final Component label;
    private final Runnable onClick;
    private final boolean primary;
    private boolean hoveredThisFrame;

    public NeonButton(Component label, float w, float h, boolean primary, Runnable onClick) {
        this.label = label;
        this.width = w;
        this.height = h;
        this.primary = primary;
        this.onClick = onClick;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        super.tick(deltaTicks);
    }

    private double lastMouseX = -1, lastMouseY = -1;

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float glowR = primary ? 12f : 0f;
        float glowAlpha = primary ? 0.55f * (0.6f + 0.4f * hover) : 0f;
        int glowColor = applyAlpha(t.glow(), glowAlpha);

        int fillTop, fillBottom;
        if (primary) {
            fillTop = t.accentArgb();
            fillBottom = t.deepArgb();
        } else {
            fillTop = lerpColor(t.raised(), t.fillAccentTop(), hover * 0.5f);
            fillBottom = lerpColor(t.raisedBottom(), t.fillAccentBottom(), hover * 0.5f);
        }

        int borderArgb = primary ? 0 : applyAlpha(t.borderStrong(), 0.3f + 0.4f * hover);
        float radius = height / 2f;

        ctx.sdf().add(SdfShape.builder()
                .rect(ax, ay, width, height)
                .radius(radius)
                .border(primary ? 0 : 1, borderArgb)
                .glow(glowR, glowColor)
                .highlight(primary ? 0.6f + 0.2f * hover : 0.2f + 0.3f * hover)
                .fill(fillTop, fillBottom)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        int textColor = primary ? NeonTheme.textOnAccent() : NeonTheme.text();
        int tw = ctx.font().width(label);
        int tx = (int) (absX() + (width - tw) / 2f);
        int ty = (int) (absY() + (height - 8) / 2f);
        g.drawString(ctx.font(), label, tx, ty, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            pressed = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && pressed) {
            pressed = false;
            if (contains(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
                onClick.run();
            }
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = lerpByte(from >>> 24, to >>> 24, t);
        int r = lerpByte(from >> 16, to >> 16, t);
        int g = lerpByte(from >> 8, to >> 8, t);
        int b = lerpByte(from, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpByte(int from, int to, float t) {
        return Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * UiEase.clamp01(t));
    }
}
