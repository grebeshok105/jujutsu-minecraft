package jujutsu.mod.client.ui.neon.layout;

import jujutsu.mod.client.ui.neon.UiComponent;
import jujutsu.mod.client.ui.neon.UiContainer;

public final class VerticalLayout extends UiContainer {
    private final float gap;
    private final float padTop, padLeft, padRight;

    public VerticalLayout(float gap, float padTop, float padLeft, float padRight) {
        this.gap = gap;
        this.padTop = padTop;
        this.padLeft = padLeft;
        this.padRight = padRight;
    }

    @Override
    public void layout() {
        float cy = padTop;
        float contentW = width - padLeft - padRight;
        for (UiComponent child : children) {
            if (!child.isVisible()) continue;
            child.setBounds(padLeft, cy, contentW, child.height());
            if (child instanceof UiContainer c) c.layout();
            cy += child.height() + gap;
        }
        super.layout();
    }
}
