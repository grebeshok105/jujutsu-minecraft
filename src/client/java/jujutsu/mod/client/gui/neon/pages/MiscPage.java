package jujutsu.mod.client.gui.neon.pages;

import java.util.List;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.NeonDropdown;
import jujutsu.mod.client.ui.neon.widget.NeonToggle;
import net.minecraft.network.chat.Component;

public final class MiscPage extends NeonPage {
    public MiscPage() {
        super(Component.literal("Misc"));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        NeonToggle notifications = new NeonToggle(Component.literal("Notifications"), true);
        notifications.setBounds(0, 24, pageW, 24);
        add(notifications);

        NeonDropdown language = new NeonDropdown(Component.literal("Language"),
                List.of(Component.literal("System Default"), Component.literal("English"), Component.literal("Russian")), 0);
        language.setBounds(0, 66, pageW, 24);
        add(language);

        NeonToggle debugOverlay = new NeonToggle(Component.literal("Debug Overlay"), false);
        debugOverlay.setBounds(0, 108, pageW, 24);
        add(debugOverlay);
    }
}
