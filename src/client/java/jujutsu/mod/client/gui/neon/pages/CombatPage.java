package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import net.minecraft.network.chat.Component;

/** Empty combat tab — shell only. */
public final class CombatPage extends NeonPage {
    public CombatPage() {
        super(Component.empty(), null);
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        // Intentionally empty.
    }
}
