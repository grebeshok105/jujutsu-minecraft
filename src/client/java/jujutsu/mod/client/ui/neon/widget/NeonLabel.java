package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.UiComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class NeonLabel extends UiComponent {
    private Component text;
    private final int color;
    private final boolean shadow;

    public NeonLabel(Component text, int color, boolean shadow) {
        this.text = text;
        this.color = color;
        this.shadow = shadow;
    }

    public NeonLabel(Component text, int color) {
        this(text, color, false);
    }

    public void setText(Component text) { this.text = text; }
    public Component text() { return text; }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        GuiGraphics g = ctx.graphics();
        g.drawString(ctx.font(), text, (int) absX(), (int) absY(), color, shadow);
    }
}
