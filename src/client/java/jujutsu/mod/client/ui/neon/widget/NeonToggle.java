package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

public final class NeonToggle extends UiComponent {
    private final Component label;
    private boolean state;
    private float knobAnim;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    private static final float TRACK_W = 36;
    private static final float TRACK_H = 16;
    private static final float KNOB_R = 6;

    public NeonToggle(Component label, boolean initial) {
        this.label = label;
        this.state = initial;
        this.knobAnim = initial ? 1f : 0f;
        this.height = 20;
        this.width = 200;
    }

    public boolean state() { return state; }
    public void setState(boolean s) { this.state = s; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        knobAnim = UiEase.approach(knobAnim, state ? 1f : 0f, 0.35f, deltaTicks);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float trackX = ax + width - TRACK_W - 4;
        float trackY = ay + (height - TRACK_H) / 2f;

        int trackFill = state ? (t.deepArgb() | 0xFF000000) : 0x80403026;
        int trackBorder = state ? t.accentArgb() : 0x2EE48A36;

        ctx.sdf().add(SdfShape.builder()
                .rect(trackX, trackY, TRACK_W, TRACK_H)
                .radius(TRACK_H / 2f)
                .border(1, trackBorder)
                .glow(state ? 6 : 0, applyAlpha(t.glow(), 0.35f))
                .highlight(0.3f)
                .fill(trackFill, trackFill)
                .build());

        float knobX = trackX + 3 + knobAnim * (TRACK_W - 2 * KNOB_R - 6);
        float knobY = trackY + (TRACK_H - 2 * KNOB_R) / 2f;
        int knobColor = state ? 0xFFFFE9D2 : 0xFF635850;
        ctx.sdf().add(SdfShape.builder()
                .rect(knobX, knobY, KNOB_R * 2, KNOB_R * 2)
                .radius(KNOB_R)
                .border(0, 0)
                .glow(state ? 5 : 0, applyAlpha(t.glow(), 0.4f))
                .highlight(0.5f)
                .fill(knobColor, knobColor)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        NeonFonts.drawVCenter(ctx.graphics(), ctx.font(), label, absX(), absY(), height, NeonTheme.textMuted());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            state = !state;
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f));
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
