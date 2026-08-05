package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.CharacterAbility;

/**
 * Todo's slot map: what each input position means for him.
 *
 * <p>The switch is exhaustive on purpose. A new {@link CharacterAbility} constant fails compilation
 * here instead of silently falling into the swap, which is what happened while the executor routed
 * every slot straight to {@link TodoBoogieWoogieRuntime}. The slots Todo does not use answer
 * {@code false} explicitly, so "he has nothing on that input" is a written decision, not an omission.
 */
public final class TodoAbilityRouter {
	private TodoAbilityRouter() {}

	public static boolean tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		return switch (ability) {
			case PRIMARY -> TodoBoogieWoogieRuntime.tryCast(todo, ability, notify);
			case PRIMARY_SNEAK -> TodoFakeClapRuntime.tryCast(todo, ability, notify);
			case SECONDARY -> TodoPairSwapRuntime.tryCast(todo, ability, notify);
			// SECONDARY_SNEAK never arrives: TodoDefinition folds it onto SECONDARY, because Shift+B is
			// B for him. The arm stays so the switch remains exhaustive without a default.
			// ATTACK_CONTEXT is genuinely empty — his melee is plain vanilla with attribute modifiers,
			// and he carries no technique weapon.
			case USE_CONTEXT -> TodoEntityMarkRuntime.tryCast(todo, ability, notify);
			// The hold gesture and its release belong to vessels with a held technique; Todo has none.
			case SECONDARY_SNEAK, ATTACK_CONTEXT, SECONDARY_SNEAK_HOLD, SECONDARY_SNEAK_RELEASE -> false;
		};
	}
}
