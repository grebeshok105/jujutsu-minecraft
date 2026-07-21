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
import jujutsu.mod.client.gui.ModernMenuScreen;
import jujutsu.mod.client.gui.NeonDashboardScreen;
import jujutsu.mod.network.NobaraActionPayload;
import jujutsu.mod.registry.JujutsuItems;

public final class JujutsuKeybinds {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/keys");

	private static KeyMapping characterSelect;
	private static KeyMapping modernMenu;
	private static KeyMapping nobaraEnlarge;
	private static KeyMapping nobaraExplosion;
	private static boolean attackWasDown;
	private static boolean modernMenuWasDown;
	private static boolean neonMenuWasDown;

	private JujutsuKeybinds() {}

	public static void register() {
		characterSelect = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.character_select",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				"key.categories.jujutsumod"
		));
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
		LOG.info("Registered keybinds: modern_menu default=N, character_select default=V");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				modernMenuWasDown = false;
				neonMenuWasDown = false;
				attackWasDown = false;
				return;
			}

			// Drain Fabric click counters.
			boolean neonClicked = drainClicks(characterSelect);
			boolean modernClicked = drainClicks(modernMenu);

			// Rising-edge on isDown + physical default keys (covers rebound-unknown / missed clicks).
			boolean neonDown = isActive(client, characterSelect, InputConstants.KEY_V);
			boolean modernDown = isActive(client, modernMenu, InputConstants.KEY_N);

			if (neonClicked || (neonDown && !neonMenuWasDown)) {
				toggleNeon(client);
			}
			if (modernClicked || (modernDown && !modernMenuWasDown)) {
				toggleModern(client);
			}

			neonMenuWasDown = neonDown;
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

	/**
	 * Key is considered held if the bound KeyMapping says so, or (when no GUI is open)
	 * the physical default key is held. Physical fallback keeps N/V working even if the
	 * options file left the bind unknown or another mod ate the click counter.
	 */
	private static boolean isActive(Minecraft client, KeyMapping mapping, int physicalFallback) {
		if (mapping.isDown()) {
			return true;
		}
		if (client.screen != null || client.getWindow() == null) {
			return false;
		}
		// Only use physical fallback when bind is unbound or still on its default key.
		if (mapping.isUnbound() || mapping.isDefault()) {
			return InputConstants.isKeyDown(client.getWindow().getWindow(), physicalFallback);
		}
		return false;
	}

	private static void toggleNeon(Minecraft client) {
		if (client.screen instanceof NeonDashboardScreen) {
			client.screen.onClose();
		} else if (client.screen == null) {
			client.setScreen(new NeonDashboardScreen());
		}
	}

	private static void toggleModern(Minecraft client) {
		if (client.screen instanceof ClickGui || client.screen instanceof ModernMenuScreen) {
			client.screen.onClose();
			return;
		}
		if (client.screen != null) {
			return;
		}
		LOG.info("Opening Rich ClickGui");
		ClickGui gui = Initialization.getInstance().getManager().getClickgui();
		if (gui != null) {
			client.setScreen(gui);
		} else {
			client.setScreen(new ModernMenuScreen());
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

