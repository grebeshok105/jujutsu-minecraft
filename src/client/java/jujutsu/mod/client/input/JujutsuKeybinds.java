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
import jujutsu.mod.client.character.ClientAbilityCooldowns;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.network.CharacterAbilityPayload;
import jujutsu.mod.registry.JujutsuItems;

public final class JujutsuKeybinds {
	private static final Logger LOG = LoggerFactory.getLogger("jujutsumod/keys");

	private static KeyMapping modernMenu;
	private static KeyMapping techniqueKey;
	private static KeyMapping secondTechniqueKey;
	private static KeyMapping thirdTechniqueKey;
	private static boolean attackWasDown;
	private static boolean modernMenuWasDown;
	private static boolean useWasDown;
	private static int ticksSinceFirstUse = Integer.MAX_VALUE;
	/** -1 = no pending sneak gesture; otherwise ticks the second technique key has been held. */
	private static int sneakSecondHeldTicks = -1;
	private static boolean sneakSecondHoldSent;
	private static boolean secondWasDown;

	/**
	 * How long a second right click has to arrive to count as a pair. Six ticks is comfortably inside a
	 * deliberate double click and comfortably outside two separate interactions.
	 *
	 * <p>This is the one place in the kit with a multi-press input, and it does not contradict the rule
	 * that a cast must stay instant: that rule is about the swap, which cannot afford to wait for a second
	 * press. Nothing here delays anything — the first click is vanilla's and always was, and only the
	 * completed pair reaches the mod at all.
	 */
	private static final int USE_PAIR_WINDOW_TICKS = 6;

	/**
	 * How long a sneaking second-technique press has to stay down to become the hold slot. Below the
	 * threshold the release sends the ordinary sneak tap, so a tap now confirms on release — up to
	 * 0.3 s later than the old instant send. That is the price of an honest hold gesture on the same
	 * key, and it is paid only while sneaking; the plain press still sends instantly.
	 */
	private static final int SECONDARY_HOLD_THRESHOLD_TICKS = 6;

	private JujutsuKeybinds() {}

	public static void register() {
		// Single menu: ClickGui on N (Neon dashboard removed).
		modernMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.modern_menu",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_N,
				"key.categories.jujutsumod"
		));
		// The two technique keys are shared by every vessel. Their ids still read "nobara_hairpin_*"
		// because that string is what vanilla writes into options.txt — renaming it would silently reset
		// everyone's binding. The displayed names are already vessel-neutral.
		techniqueKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.nobara_hairpin_enlarge",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_R,
				"key.categories.jujutsumod"
		));
		secondTechniqueKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.nobara_hairpin_explosion",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_B,
				"key.categories.jujutsumod"
		));
		// The third technique key is new with the shadow-drop slot, so its id can say what it is.
		thirdTechniqueKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.jujutsumod.third_technique",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_V,
				"key.categories.jujutsumod"
		));
		LOG.info("Registered keybinds: menu default=N (ClickGui), combat R/B/V");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				modernMenuWasDown = false;
				attackWasDown = false;
				useWasDown = false;
				ticksSinceFirstUse = Integer.MAX_VALUE;
				sneakSecondHeldTicks = -1;
				sneakSecondHoldSent = false;
				secondWasDown = false;
				return;
			}

			boolean modernClicked = drainClicks(modernMenu);
			boolean modernDown = isActive(client, modernMenu, InputConstants.KEY_N);

			if (modernClicked || (modernDown && !modernMenuWasDown)) {
				toggleModern(client);
			}
			modernMenuWasDown = modernDown;

			// No hold threshold and no double tap on the technique key: a cast has to stay instant, and
			// two casts on one key have to be typed identically fast.
			while (techniqueKey.consumeClick()) {
				sendCharacterAbility(client, slot(client, CharacterAbility.PRIMARY, CharacterAbility.PRIMARY_SNEAK));
			}
			// The third technique key mirrors the first: instant send, no hold gestures. Sneaking names
			// a second slot on the same key (Todo's target swap), exactly like the technique key pair.
			while (thirdTechniqueKey.consumeClick()) {
				sendCharacterAbility(client, slot(client, CharacterAbility.TERTIARY, CharacterAbility.TERTIARY_SNEAK));
			}
			tickSecondTechnique(client);

			boolean attackDown = client.options.keyAttack.isDown();
			// The weapon check is the last vessel-specific thing left in this file. It stays until the
			// client-side vessel definitions can answer "is this stack my technique weapon".
			if (attackDown && !attackWasDown && client.screen == null
					&& isTechniqueWeapon(client.player.getMainHandItem(), client.player.getOffhandItem())) {
				sendCharacterAbility(client, CharacterAbility.ATTACK_CONTEXT);
			}
			attackWasDown = attackDown;

			// The right click is vanilla's key, and the first press of a pair is vanilla's press: it has
			// already opened the chest or mounted the horse before this handler runs, and that is accepted
			// rather than worked around. Only a completed pair sends anything, so an ordinary right click
			// costs no packet at all.
			if (ticksSinceFirstUse < Integer.MAX_VALUE) {
				ticksSinceFirstUse++;
			}
			boolean useDown = client.options.keyUse.isDown();
			if (useDown && !useWasDown && client.screen == null) {
				if (ticksSinceFirstUse <= USE_PAIR_WINDOW_TICKS) {
					sendCharacterAbility(client, CharacterAbility.USE_CONTEXT);
					// Consumed, so a third click starts a fresh pair rather than firing again.
					ticksSinceFirstUse = Integer.MAX_VALUE;
				} else {
					ticksSinceFirstUse = 0;
				}
			}
			useWasDown = useDown;
		});
	}

	/**
	 * The second technique key is the only one with a hold gesture, and only while sneaking. A plain
	 * press still sends {@code SECONDARY} instantly. A sneaking press is buffered: released inside
	 * {@link #SECONDARY_HOLD_THRESHOLD_TICKS} it is the ordinary {@code SECONDARY_SNEAK} tap; held past
	 * it the client sends {@code SECONDARY_SNEAK_HOLD} once, and {@code SECONDARY_SNEAK_RELEASE} when
	 * the key finally comes up (a screen opening mid-hold releases the mapping, which reads as the same
	 * thing). The pairing is never trusted: a release with no live server state is refused there.
	 */
	private static void tickSecondTechnique(Minecraft client) {
		boolean down = secondTechniqueKey.isDown();
		boolean clicked = drainClicks(secondTechniqueKey);
		boolean pressed = clicked || (down && !secondWasDown);
		secondWasDown = down;
		if (sneakSecondHeldTicks < 0) {
			if (pressed) {
				if (client.player.isShiftKeyDown()) {
					sneakSecondHeldTicks = 0;
					sneakSecondHoldSent = false;
				} else {
					sendCharacterAbility(client, CharacterAbility.SECONDARY);
				}
			}
			return;
		}
		if (down) {
			sneakSecondHeldTicks++;
			if (!sneakSecondHoldSent && sneakSecondHeldTicks >= SECONDARY_HOLD_THRESHOLD_TICKS) {
				sneakSecondHoldSent = true;
				sendCharacterAbility(client, CharacterAbility.SECONDARY_SNEAK_HOLD);
			}
			return;
		}
		sendCharacterAbility(client, sneakSecondHoldSent
				? CharacterAbility.SECONDARY_SNEAK_RELEASE
				: CharacterAbility.SECONDARY_SNEAK);
		sneakSecondHeldTicks = -1;
		sneakSecondHoldSent = false;
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

	/**
	 * The input contract, in one expression: a technique key plus whether the player is sneaking names a
	 * slot. Nothing here knows what any vessel does with the slot it names — that belongs to the vessel's
	 * own router on the server. A vessel with nothing on a slot simply answers {@code false} there.
	 */
	private static CharacterAbility slot(Minecraft client, CharacterAbility tap, CharacterAbility sneak) {
		return client.player != null && client.player.isShiftKeyDown() ? sneak : tap;
	}

	private static JujutsuCharacter selectedCharacter(Minecraft client) {
		return client.player == null ? JujutsuCharacter.NONE : ClientCharacterSelectionManager.characterOrNone(client.player.getUUID());
	}

	/**
	 * The only vessel question this file asks: am I one at all. Staying silent for {@code NONE} keeps a
	 * stray key press from earning a "pick a character first" line the player did not ask for.
	 */
	private static void sendCharacterAbility(Minecraft client, CharacterAbility ability) {
		JujutsuCharacter character = selectedCharacter(client);
		if (character == JujutsuCharacter.NONE) {
			return;
		}
		if (!ClientAbilityCooldowns.isReady(character, ability)) {
			return;
		}
		if (ClientPlayNetworking.canSend(CharacterAbilityPayload.TYPE)) {
			// Stamped with the vessel the player can see, so a press made in the gap between applying a
			// switch locally and the server confirming it is refused rather than cast by the old vessel.
			ClientPlayNetworking.send(new CharacterAbilityPayload(ability.networkId(), character.id()));
		}
	}

	private static boolean isTechniqueWeapon(ItemStack mainHand, ItemStack offHand) {
		return isTechniqueWeapon(mainHand) || isTechniqueWeapon(offHand);
	}

	private static boolean isTechniqueWeapon(ItemStack stack) {
		return stack.is(JujutsuItems.STRAW_DOLL_HAMMER) || stack.is(JujutsuItems.PROJECTJJK_STRAW_DOLL_HAMMER);
	}
}
