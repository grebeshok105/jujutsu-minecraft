package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;

/**
 * Todo's slot map: what each input position means for him.
 *
 * <p>The switch is exhaustive on purpose. A new {@link CharacterAbility} constant fails compilation
 * here instead of silently falling into the swap, which is what happened while the executor routed
 * every slot straight to {@link TodoBoogieWoogieRuntime}. The slots Todo does not use answer
 * {@link AbilityResult#UNHANDLED_FAILURE} explicitly, so "he has nothing on that input" is a written
 * decision, not an omission. His runtimes keep their boolean contract — this router maps
 * {@code true -> SUCCESS}, {@code false -> UNHANDLED_FAILURE} (no router-level fallback exists for him).
 */
public final class TodoAbilityRouter {
	private TodoAbilityRouter() {}

	public static AbilityResult tryCast(ServerPlayer todo, CharacterAbility ability, boolean notify) {
		return switch (ability) {
			case PRIMARY -> TodoBoogieWoogieRuntime.tryCast(todo, ability, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case PRIMARY_SNEAK -> TodoFakeClapRuntime.tryCast(todo, ability, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			// B and Shift+B are one runtime with two casts: B marks and commits the pair, Shift+B runs the
			// triple cycle on the live selection. They have separate cooldown slots on purpose.
			case SECONDARY, SECONDARY_SNEAK -> TodoPairSwapRuntime.tryCast(todo, ability, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			// ATTACK_CONTEXT is genuinely empty — his melee is plain vanilla with attribute modifiers,
			// and he carries no technique weapon.
			// USE_CONTEXT keeps its wire id and its client detection (the paired right click), but the
			// body-mark ability is gone and no other vessel listens on it in this slice.
			case USE_CONTEXT -> AbilityResult.UNHANDLED_FAILURE;
			// The hold gesture and its release belong to vessels with a held technique; Todo has none.
			// V throws the stone and, while one is in flight, self-swaps with it; Shift+V swaps an aimed
			// target with the stone. Both arms live in TodoStoneRuntime.
			case TERTIARY, TERTIARY_SNEAK -> TodoStoneRuntime.tryCast(todo, ability, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case ATTACK_CONTEXT, SECONDARY_SNEAK_HOLD, SECONDARY_SNEAK_RELEASE -> AbilityResult.UNHANDLED_FAILURE;
		};
	}
}
