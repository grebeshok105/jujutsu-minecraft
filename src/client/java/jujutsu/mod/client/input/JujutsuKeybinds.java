package jujutsu.mod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import jujutsu.mod.client.gui.CharacterSelectScreen;
import jujutsu.mod.network.NobaraActionPayload;

public final class JujutsuKeybinds {
	private static KeyMapping characterSelect;
	private static KeyMapping nobaraEnlarge;
	private static KeyMapping nobaraExplosion;

	private JujutsuKeybinds() {}

	public static void register() {
		characterSelect = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.character_select",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				"key.categories.jujutsumod"
		));
		nobaraEnlarge = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.nobara_hairpin_enlarge",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_R,
				"key.categories.jujutsumod"
		));
		nobaraExplosion = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.nobara_hairpin_explosion",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_B,
				"key.categories.jujutsumod"
		));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (characterSelect.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new CharacterSelectScreen());
				}
			}
			while (nobaraEnlarge.consumeClick()) {
				sendNobaraAction(NobaraActionPayload.HAIRPIN_ENLARGE);
			}
			while (nobaraExplosion.consumeClick()) {
				sendNobaraAction(NobaraActionPayload.HAIRPIN_EXPLOSION);
			}
		});
	}

	private static void sendNobaraAction(int action) {
		if (ClientPlayNetworking.canSend(NobaraActionPayload.TYPE)) {
			ClientPlayNetworking.send(new NobaraActionPayload(action));
		}
	}
}
