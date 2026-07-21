package jujutsu.mod.client.gui.neon.pages;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.gui.neon.NeonPage;
import jujutsu.mod.client.ui.neon.NeonContext;

/**
 * Empty character tab — shell only.
 * Selection state is still readable for theme (defaults to current vessel / NONE).
 */
public final class CharacterPage extends NeonPage {
    private JujutsuCharacter selection;

    public CharacterPage(Runnable closeAction) {
        super(Component.empty(), null);
        this.selection = initialSelection();
    }

    private static JujutsuCharacter initialSelection() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            return ClientCharacterSelectionManager.characterOrNone(mc.player.getUUID());
        }
        return JujutsuCharacter.NONE;
    }

    @Override
    public void buildContent(float pageW, float pageH) {
        // Intentionally empty — no roster, abilities, or Confirm/Cancel.
    }

    public JujutsuCharacter selection() {
        return selection;
    }

    @Override
    public void renderSurface(NeonContext ctx) {
        if (!isVisible()) return;
        // Skip NeonPage title chrome (empty title still drew a rule line).
    }

    @Override
    public void renderText(NeonContext ctx) {
        if (!isVisible()) return;
        // No title / children.
    }
}
