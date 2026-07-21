package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.UiEase;
import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

/** Mockup section-label: small uppercase text with a trailing rule line. */
public final class SectionLabel extends UiComponent {
    private final Component text;

    public SectionLabel(Component text) {
        this.text = NeonFonts.wrap(text);
        this.height = 12;
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        int tw = NeonFonts.width(ctx.font(), text);
        NeonTheme t = ctx.theme();
        float ruleW = width - tw - 8;
        if (ruleW > 0) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(absX() + tw + 8, absY() + 3.5f, ruleW, 1)
                    .radius(0).border(0, 0).glow(0, 0)
                    .fill(applyAlpha(t.accentArgb(), 0.10f), applyAlpha(t.accentArgb(), 0.10f))
                    .build());
        }
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        NeonFonts.draw(ctx.graphics(), ctx.font(), text, absX(), absY(), NeonTheme.textDim());
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) / 255f) * UiEase.clamp01(alpha) * 255f);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
