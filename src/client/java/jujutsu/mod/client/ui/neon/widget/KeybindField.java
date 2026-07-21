package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

public final class KeybindField extends UiComponent {
    private final Component label;
    private String keyName;
    private boolean listening;
    private boolean hoveredThisFrame;
    private double lastMouseX = -1, lastMouseY = -1;

    public KeybindField(Component label, String initialKey) {
        this.label = label;
        this.keyName = initialKey;
        this.height = 20;
        this.width = 200;
    }

    public String keyName() { return keyName; }
    public boolean isListening() { return listening; }

    public void updateMouse(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
    }

    @Override
    protected boolean isHovered() { return hoveredThisFrame; }

    @Override
    public void tick(float deltaTicks) {
        hoveredThisFrame = contains(lastMouseX, lastMouseY);
        super.tick(deltaTicks);
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        float ax = absX(), ay = absY();
        float fieldW = 60;
        float fieldX = ax + width - fieldW;

        int borderA = listening ? t.borderStrong() : (hoveredThisFrame ? t.border() : 0x20505050);
        ctx.sdf().add(SdfShape.builder()
                .rect(fieldX, ay, fieldW, height)
                .radius(5)
                .border(1, borderA)
                .glow(listening ? 6 : 0, applyAlpha(t.glow(), 0.3f))
                .highlight(0.2f)
                .fill(listening ? 0x30E48A36 : 0x40202020, listening ? 0x20E48A36 : 0x40181818)
                .build());
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        float ax = absX(), ay = absY();
        NeonFonts.drawVCenter(ctx.graphics(), ctx.font(), label, ax, ay, height, NeonTheme.textMuted());

        float fieldW = 60;
        float fieldX = ax + width - fieldW;
        String display = listening ? "..." : keyName;
        int color = listening ? NeonTheme.text() : NeonTheme.textDim();
        NeonFonts.drawCenteredV(ctx.graphics(), ctx.font(), NeonFonts.literal(display),
                fieldX + fieldW / 2f, ay, height, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && contains(mouseX, mouseY)) {
            listening = !listening;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                listening = false;
            } else {
                keyName = com.mojang.blaze3d.platform.InputConstants.getKey(keyCode, scanCode).getDisplayName().getString();
                listening = false;
            }
            return true;
        }
        return false;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
