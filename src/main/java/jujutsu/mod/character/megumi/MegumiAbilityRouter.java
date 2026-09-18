package jujutsu.mod.character.megumi;

import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.AbilityResult;
import jujutsu.mod.character.CharacterAbility;

/**
 * Megumi's slot map: what each input position means for him.
 *
 * <p>The switch is exhaustive on purpose — a new {@link CharacterAbility} constant fails compilation
 * here instead of falling into a {@code default}. One gate lives above the switch and not in the
 * shared executor because it is his alone: an active shadow move locks every other technique, and a
 * repeat tap of the travel slot is how the player asks to leave the shadow early.
 *
 * <p>His runtimes keep their boolean contract — this router maps {@code true -> SUCCESS},
 * {@code false -> UNHANDLED_FAILURE} (no router-level fallback exists for him).
 */
public final class MegumiAbilityRouter {
	private MegumiAbilityRouter() {}

	public static AbilityResult tryCast(ServerPlayer player, CharacterAbility ability, boolean notify) {
		if (MegumiShadowMoveRuntime.locksAbilities(player)) {
			return MegumiShadowMoveRuntime.handleWhileActive(player, ability, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
		}
		return switch (ability) {
			case PRIMARY -> MegumiShikigamiRuntime.tryPrimary(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case PRIMARY_SNEAK -> MegumiShikigamiRuntime.trySic(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY -> MegumiShadowTrapRuntime.tryCast(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY_SNEAK -> MegumiShadowMoveRuntime.tryTap(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY_SNEAK_HOLD -> MegumiShadowMoveRuntime.tryHoldStart(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case SECONDARY_SNEAK_RELEASE -> MegumiShadowMoveRuntime.tryRelease(player)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case TERTIARY -> MegumiShadowDropRuntime.tryCast(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case TERTIARY_SNEAK -> MegumiShikigamiRuntime.tryCycle(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			// #108: the partial key's two edges. Neither ever carries a cooldown, so both reach this
			// router on the same tick they are pressed and released.
			case PARTIAL -> MegumiPartialRuntime.tryPartial(player, notify)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case PARTIAL_RELEASE -> MegumiPartialRuntime.tryPartialRelease(player)
					? AbilityResult.SUCCESS : AbilityResult.UNHANDLED_FAILURE;
			case ATTACK_CONTEXT, USE_CONTEXT -> AbilityResult.UNHANDLED_FAILURE;
		};
	}
}
