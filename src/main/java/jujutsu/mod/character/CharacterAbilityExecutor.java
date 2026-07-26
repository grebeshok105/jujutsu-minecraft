package jujutsu.mod.character;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-only dispatcher for shared active-ability slots.
 *
 * <p>It owns the two checks that are the same for everyone — having a vessel at all, and the slot's
 * cooldown — and then asks the vessel. It names no vessel, so a new one never edits this file.
 */
public final class CharacterAbilityExecutor {
	private CharacterAbilityExecutor() {}

	public static boolean tryCast(ServerPlayer player, CharacterAbility ability, boolean notify) {
		JujutsuCharacter character = CharacterSelectionManager.selected(player);
		if (character == JujutsuCharacter.NONE) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.character.action.not_selected"), true);
			}
			return false;
		}
		// Folded first, so a vessel that treats two inputs as one shares their cooldown instead of having
		// the second quietly bypass the first.
		CharacterDefinition definition = JujutsuCharacters.definition(character);
		CharacterAbility slot = definition.canonicalSlot(ability);
		if (!CharacterAbilityCooldowns.isReady(player, slot)) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.character.action.cooldown"), true);
			}
			return false;
		}
		return definition.tryCast(player, slot, notify);
	}
}
