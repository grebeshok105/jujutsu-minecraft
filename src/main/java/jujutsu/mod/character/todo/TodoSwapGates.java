package jujutsu.mod.character.todo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import jujutsu.mod.combat.CombatStagger;

/**
 * The preconditions for clapping, owned in one place.
 *
 * <p>Todo's feint has to be indistinguishable from the real swap, and that includes which casts get
 * refused: a feint that were allowed with a sword in hand would tell an observer it was a feint. So
 * both paths read this truth table instead of repeating the checks.
 */
public final class TodoSwapGates {
	private TodoSwapGates() {}

	/** Why a clap was refused. UNAVAILABLE is silent — caster state is not worth an actionbar line. */
	public enum ClapGate {
		ALLOWED,
		UNAVAILABLE,
		HANDS_FULL
	}

	/** Pure policy, so the swap and the feint provably share one answer. */
	public static ClapGate evaluate(boolean spectator, boolean alive, boolean unsafeTransport, boolean staggered, boolean handsEmpty) {
		if (spectator || !alive || unsafeTransport || staggered) {
			return ClapGate.UNAVAILABLE;
		}
		return handsEmpty ? ClapGate.ALLOWED : ClapGate.HANDS_FULL;
	}

	static ClapGate evaluate(ServerPlayer todo) {
		return evaluate(
				todo.isSpectator(),
				todo.isAlive(),
				TodoTargetSafety.hasUnsafeTransportState(todo.isPassenger(), todo.isVehicle(), false),
				CombatStagger.GLOBAL.isStaggered(todo.getUUID(), todo.level().getGameTime()),
				// Authoritative empty-hands gate: any held item blocks the clap with no partial effects.
				isEmptyHand(todo.getMainHandItem()) && isEmptyHand(todo.getOffhandItem()));
	}

	static boolean isEmptyHand(ItemStack stack) {
		return stack == null || stack.isEmpty();
	}
}
