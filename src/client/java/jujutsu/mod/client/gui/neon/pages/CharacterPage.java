package jujutsu.mod.client.gui.neon.pages;

import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.widget.NeonLabel;
import net.minecraft.network.chat.Component;

public final class CharacterPage extends NeonPage {
    public CharacterPage() {
        super(Component.literal("Character"));
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        NeonLabel placeholder = new NeonLabel(
                Component.literal("Character roster (Stage 5)"),
                jujutsu.mod.client.ui.neon.NeonTheme.textDim(), false);
        placeholder.setBounds(0, 30, pageW, 10);
        add(placeholder);
    }
}
