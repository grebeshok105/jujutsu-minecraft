package jujutsu.mod.client.gui.neon.pages;

import java.util.List;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;
import jujutsu.mod.client.ui.neon.widget.NeonSlider;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import net.minecraft.network.chat.Component;

public final class CombatPage extends NeonPage {
    public CombatPage() {
        super(Component.literal("Combat"));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        NeonToggle autoAttack = new NeonToggle(Component.literal("Auto Attack"), false);
        autoAttack.setBounds(0, 24, pageW, 24);
        add(autoAttack);

        NeonSlider reach = new NeonSlider(Component.literal("Reach Distance"), 0.5f);
        reach.setBounds(0, 60, pageW, 30);
        add(reach);

        NeonToggle hitMarkers = new NeonToggle(Component.literal("Hit Markers"), true);
        hitMarkers.setBounds(0, 102, pageW, 24);
        add(hitMarkers);

        NeonDropdown mode = new NeonDropdown(Component.literal("Combat Mode"),
                List.of(Component.literal("Standard"), Component.literal("Aggressive"), Component.literal("Defensive")), 0);
        mode.setBounds(0, 150, pageW, 24);
        add(mode);
    }
}
