package jujutsu.mod.client.gui.neon;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.NeonTheme;
import jujutsu.mod.client.ui.neon.UiContainer;
import jujutsu.mod.client.ui.neon.render.SdfShape;
import net.minecraft.network.chat.Component;

public abstract class NeonPage extends UiContainer {
    private final Component title;
    private final Component subtitle;

    protected NeonPage(Component title, Component subtitle) {
        this.title = NeonFonts.wrap(title);
        this.subtitle = subtitle == null ? null : NeonFonts.wrap(subtitle);
    }

    public Component title() { return title; }

    public abstract void buildContent(float pageW, float pageH);

    /** Vertical offset where page content should start (below title + subtitle). */
    public float contentTop() { return 30f; }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        int titleW = NeonFonts.width(ctx.font(), title);
        float ax = absX(), ay = absY();
        float ruleX = ax + titleW + 10;
        float ruleW = width - titleW - 10;
        if (ruleW > 0) {
            ctx.sdf().add(SdfShape.builder()
                    .rect(ruleX, ay + 3.5f, ruleW, 1)
                    .radius(0).border(0, 0).glow(0, 0)
                    .fill(ctx.theme().border(), ctx.theme().border())
                    .build());
        }
        super.renderSurface(ctx);
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        int line = Math.max(8, ctx.font().lineHeight);
        NeonFonts.draw(ctx.graphics(), ctx.font(), title, absX(), absY(), NeonTheme.text());
        if (subtitle != null) {
            NeonFonts.draw(ctx.graphics(), ctx.font(), subtitle, absX(), absY() + line + 2, NeonTheme.textDim());
        }
        super.renderText(ctx);
    }
}
