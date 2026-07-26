package jujutsu.mod.character;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.todo.TodoBoogieWoogieRuntime;

/** Server-only dispatcher for shared active-ability slots. */
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
		if (!CharacterAbilityCooldowns.isReady(player, ability)) {
			if (notify) {
				player.displayClientMessage(Component.translatable("message.jujutsumod.character.action.cooldown"), true);
			}
			return false;
		}
		return switch (character) {
			case TODO -> TodoBoogieWoogieRuntime.tryCast(player, ability, notify);
			case NOBARA, NONE -> false;
		};
	}
}
