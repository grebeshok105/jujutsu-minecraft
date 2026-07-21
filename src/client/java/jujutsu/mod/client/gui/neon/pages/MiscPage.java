package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import net.minecraft.network.chat.Component;

/** Empty misc tab — shell only. */
public final class MiscPage extends NeonPage {
    public MiscPage() {
        super(Component.empty(), null);
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        // Intentionally empty.
    }
}
