package jujutsu.mod.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jujutsu.mod.client.rich.Initialization;
import jujutsu.mod.client.rich.screens.clickgui.ClickGui;
import jujutsu.mod.network.NobaraActionPayload;
import jujutsu.mod.registry.JujutsuItems;

public final class JujutsuKeybinds {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/keys");

	private static KeyMapping modernMenu;
	private static KeyMapping nobaraEnlarge;
	private static KeyMapping nobaraExplosion;
	private static boolean attackWasDown;
	private static boolean modernMenuWasDown;

	private JujutsuKeybinds() {}

	public static void register() {
		// Single menu: ClickGui on N (Neon dashboard removed).
		modernMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.modern_menu",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_N,
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
		LOG.info("Registered keybinds: menu default=N (ClickGui), combat R/B");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				modernMenuWasDown = false;
				attackWasDown = false;
				return;
			}

			boolean modernClicked = drainClicks(modernMenu);
			boolean modernDown = isActive(client, modernMenu, InputConstants.KEY_N);

			if (modernClicked || (modernDown && !modernMenuWasDown)) {
				toggleModern(client);
			}
			modernMenuWasDown = modernDown;

			while (nobaraEnlarge.consumeClick()) {
				sendNobaraAction(client.player.isShiftKeyDown()
						? NobaraActionPayload.SELF_RESONANCE
						: NobaraActionPayload.HAIRPIN_DIRECTED);
			}
			while (nobaraExplosion.consumeClick()) {
				sendNobaraAction(client.player.isShiftKeyDown()
						? NobaraActionPayload.NAIL_TRAP
						: NobaraActionPayload.HAIRPIN_MASS);
			}

			boolean attackDown = client.options.keyAttack.isDown();
			if (attackDown && !attackWasDown && client.screen == null
					&& isHoldingNobaraHammer(client.player.getMainHandItem(), client.player.getOffhandItem())) {
				sendNobaraAction(NobaraActionPayload.HAMMER_CONTEXT);
			}
			attackWasDown = attackDown;
		});
	}

	private static boolean drainClicks(KeyMapping mapping) {
		boolean clicked = false;
		while (mapping.consumeClick()) {
			clicked = true;
		}
		return clicked;
	}

	private static boolean isActive(Minecraft client, KeyMapping mapping, int physicalFallback) {
		if (mapping.isDown()) {
			return true;
		}
		if (client.screen != null || client.getWindow() == null) {
			return false;
		}
		if (mapping.isUnbound() || mapping.isDefault()) {
			return InputConstants.isKeyDown(client.getWindow().getWindow(), physicalFallback);
		}
		return false;
	}

	private static void toggleModern(Minecraft client) {
		if (client.screen instanceof ClickGui) {
			client.screen.onClose();
			return;
		}
		if (client.screen != null) {
			return;
		}
		ClickGui gui = Initialization.getInstance().getManager().getClickgui();
		if (gui != null) {
			LOG.info("Opening ClickGui");
			client.setScreen(gui);
		} else {
			LOG.error("ClickGui failed to initialize");
		}
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
