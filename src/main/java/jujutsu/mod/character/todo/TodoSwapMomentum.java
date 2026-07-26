package jujutsu.mod.character.todo;

/**
 * When a landed swap's window is spent, as pure policy so the truth table can be tested rather than
 * argued about. Same shape as {@link TodoSwapGates}, and for the same reason.
 *
 * <p>The interesting entry is {@code reentrantBonus}. Todo's Black Flash applies its extra damage by
 * calling {@code hurtServer} again from inside the damage event, so the listener sees the <em>same swing</em>
 * twice: once nested for the bonus hit, once for the base hit. Spending on the nested pass would anchor
 * the stagger and the cue to whichever pass a hidden ten-percent dice roll happened to produce.
 */
public final class TodoSwapMomentum {
	private TodoSwapMomentum() {}

	public enum Spend {
		SPEND,
		IGNORE
	}

	public static Spend decide(
			boolean blocked,
			float damageTaken,
			boolean reentrantBonus,
			boolean directIsOwner,
			boolean isTodoVessel,
			boolean attackerPlayable,
			boolean hasMomentum
	) {
		if (!hasMomentum) {
			return Spend.IGNORE;
		}
		// A shield or a zero-damage hit is not a hit, so the window survives it.
		if (blocked || damageTaken <= 0.0f) {
			return Spend.IGNORE;
		}
		if (reentrantBonus) {
			return Spend.IGNORE;
		}
		// The attacker must be the melee source itself: an arrow he fired is not the hit this bought.
		if (!directIsOwner || !isTodoVessel || !attackerPlayable) {
			return Spend.IGNORE;
		}
		return Spend.SPEND;
	}
}
