package jujutsu.mod.client.gui.neon;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.UiContainer;
import net.minecraft.network.chat.Component;

public abstract class NeonPage extends UiContainer {
    private final Component title;

    protected NeonPage(Component title) {
        this.title = title;
    }

    public Component title() { return title; }

    public abstract void buildContent(float pageW, float pageH);

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        super.renderSurface(ctx);
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        ctx.graphics().drawString(ctx.font(), title, (int) absX(), (int) absY(), jujutsu.mod.client.ui.neon.NeonTheme.text(), false);
        super.renderText(ctx);
    }
}
