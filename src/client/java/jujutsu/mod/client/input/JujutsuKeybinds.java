package jujutsu.mod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import jujutsu.mod.client.gui.CharacterSelectScreen;

public final class JujutsuKeybinds {
	private static KeyMapping characterSelect;

	private JujutsuKeybinds() {}

	public static void register() {
		characterSelect = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.character_select",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				"key.categories.jujutsumod"
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (characterSelect.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new CharacterSelectScreen());
				}
			}
		});
	}
}
