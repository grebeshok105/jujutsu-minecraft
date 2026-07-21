package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import net.minecraft.network.chat.Component;

/** Empty visuals tab — shell only. */
public final class VisualsPage extends NeonPage {
    public VisualsPage() {
        super(Component.empty(), null);
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        // Intentionally empty.
    }
}
