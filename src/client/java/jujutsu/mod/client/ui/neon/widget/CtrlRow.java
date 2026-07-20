package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiContainer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

/**
 * Mockup ctrl-row: an inset panel holding a control widget (whose own label acts as the row
 * title) plus a description line beneath it.
 */
public final class CtrlRow extends UiContainer {
    private final Component desc;
    private final UiComponent control;

    public CtrlRow(Component desc, UiComponent control) {
        this.desc = desc;
        this.control = control;
        this.height = 46;
        if (control != null) add(control);
    }

    @Override
    public void layout() {
        if (control != null) {
            control.setBounds(12, 5, width - 24, control.height());
        }
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
        ctx.graphics().drawString(ctx.font(), desc, (int) (absX() + 12), (int) (absY() + 28), NeonTheme.textDim(), false);
        super.renderText(ctx);
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * jujutsu.mod.client.ui.UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
