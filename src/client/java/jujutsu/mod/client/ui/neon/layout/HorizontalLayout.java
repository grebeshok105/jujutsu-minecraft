package jujutsu.mod.client.ui.neon.layout;

import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiContainer;

public final class HorizontalLayout extends UiContainer {
    private final float gap;
    private final float padLeft, padTop;

    public HorizontalLayout(float gap, float padLeft, float padTop) {
        this.gap = gap;
        this.padLeft = padLeft;
        this.padTop = padTop;
    }

    @Override
    public void layout() {
        float cx = padLeft;
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            child.setBounds(cx, padTop, child.width(), child.height());
            if (child instanceof UiContainer c) c.layout();
            cx += child.width() + gap;
        }
        super.layout();
    }
}
