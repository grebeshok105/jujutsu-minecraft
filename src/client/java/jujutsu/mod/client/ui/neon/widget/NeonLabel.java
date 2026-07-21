package jujutsu.mod.client.ui.neon.widget;

import jujutsu.mod.client.ui.neon.NeonContext;
import jujutsu.mod.client.ui.neon.NeonFonts;
import jujutsu.mod.client.ui.neon.UiComponent;
import net.minecraft.network.chat.Component;

public final class NeonLabel extends UiComponent {
    private Component text;
    private final int color;

    public NeonLabel(Component text, int color, boolean shadow) {
        this.text = NeonFonts.wrap(text);
        this.color = color;
        // shadow ignored — neon UI uses flat text only for consistency
    }

    public NeonLabel(Component text, int color) {
        this(text, color, false);
    }

    public void setText(Component text) { this.text = NeonFonts.wrap(text); }
    public Component text() { return text; }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        NeonFonts.draw(ctx.graphics(), ctx.font(), text, absX(), absY(), color);
    }
}
