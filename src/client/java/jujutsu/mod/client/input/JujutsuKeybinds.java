package jujutsu.mod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.client.gui.CharacterSelectScreen;
import jujutsu.mod.client.gui.SdfProbeScreen;
import jujutsu.mod.network.NobaraActionPayload;
import jujutsu.mod.registry.JujutsuItems;

public final class JujutsuKeybinds {
	private static KeyMapping characterSelect;
	private static KeyMapping nobaraEnlarge;
	private static KeyMapping nobaraExplosion;
	private static boolean attackWasDown;

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
					// TEMPORARY (Stage 2): opens the SDF shader probe. Replaced by the real
					// neon dashboard in Stage 3.
					client.setScreen(new SdfProbeScreen());
				}
			}
			while (nobaraEnlarge.consumeClick()) {
				sendNobaraAction(client.player != null && client.player.isShiftKeyDown() ? NobaraActionPayload.SELF_RESONANCE : NobaraActionPayload.HAIRPIN_DIRECTED);
			}
			while (nobaraExplosion.consumeClick()) {
				sendNobaraAction(client.player != null && client.player.isShiftKeyDown() ? NobaraActionPayload.NAIL_TRAP : NobaraActionPayload.HAIRPIN_MASS);
			}
			boolean attackDown = client.options.keyAttack.isDown();
			if (attackDown && !attackWasDown && client.player != null && client.screen == null && isHoldingNobaraHammer(client.player.getMainHandItem(), client.player.getOffhandItem())) {
				sendNobaraAction(NobaraActionPayload.HAMMER_CONTEXT);
			}
			attackWasDown = attackDown;
		});
	}

	private static void sendNobaraAction(int action) {
		if (ClientPlayNetworking.canSend(NobaraActionPayload.TYPE)) {
			ClientPlayNetworking.send(new NobaraActionPayload(action));
		}
	}

	private static boolean isHoldingNobaraHammer(ItemStack mainHand, ItemStack offHand) {
		return isNobaraHammer(mainHand) || isNobaraHammer(offHand);
	}

	private static boolean isNobaraHammer(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}
}
