package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;

/**
 * Todo's slot map. The switch is exhaustive on purpose: a new {@link CharacterAbility} constant fails
 * compilation here instead of silently falling into the swap, which is what happened while the
 * executor routed every slot straight to {@link TodoBoogieWoogieRuntime}.
 */
public final class TodoAbilityRouter {
	private TodoAbilityRouter() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		return switch (ability) {
			case PRIMARY -> TodoBoogieWoogieRuntime.tryCast(todo, ability, notify);
			case SECONDARY -> TodoFakeClapRuntime.tryCast(todo, ability, notify);
			case TERTIARY -> TodoPairSwapRuntime.tryCast(todo, ability, notify);
		};
	}
}
