package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiContainer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

/**
 * Compact ctrl-row: control on the top line, muted description under it.
 * Height stays tight so shell pages fit inside the dashboard window.
 */
public final class CtrlRow extends UiContainer {
    private final Component desc;
    private final UiComponent control;

    public CtrlRow(Component desc, UiComponent control) {
        this.desc = NeonFonts.wrap(desc);
        this.control = control;
        this.height = preferredHeight();
        if (control != null) add(control);
    }

    public float preferredHeight() {
        float controlH = control != null ? Math.max(18f, control.height()) : 18f;
        // 3 pad + control + 2 gap + 9 desc + 4 bottom
        return 3f + controlH + 2f + 9f + 4f;
    }

    @Override
    public void layout() {
        if (control != null) {
            float controlH = Math.max(18f, control.height());
            control.setBounds(10, 3, width - 20, controlH);
        }
        this.height = preferredHeight();
        super.layout();
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        NeonTheme t = ctx.theme();
        ctx.sdf().add(SdfShape.builder()
                .rect(absX(), absY(), width, height)
                .radius(6)
                .border(1, applyAlpha(t.accentArgb(), 0.10f))
                .glow(0, 0).highlight(0.10f)
                .fill(t.panelInset(), t.panelInset())
                .build());
        super.renderSurface(ctx);
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        float controlH = control != null ? Math.max(18f, control.height()) : 18f;
        float descY = absY() + 3f + controlH + 2f;
        ctx.graphics().drawString(ctx.font(), desc, (int) (absX() + 12), (int) descY, NeonTheme.textDim(), false);
        super.renderText(ctx);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * jujutsu.mod.client.ui.UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
