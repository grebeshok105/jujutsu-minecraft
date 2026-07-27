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

	/**
	 * The same table, entered from a kill instead of from applied damage.
	 *
	 * <p>There are two entries because {@code AFTER_DAMAGE} does not fire on a killing blow, so a kill has
	 * to be seen separately or a finishing hit would silently refund the window. There is only one
	 * <em>table</em> because this delegates: the kill path used to make its own three-check decision and
	 * consequently spent a melee window on a bow kill, which is exactly the class of thing a shared truth
	 * table exists to prevent.
	 *
	 * <p>Two of {@code decide}'s arms cannot discriminate here and are supplied as the only values a kill
	 * can have, rather than being skipped. A kill was not blocked — a shielded hit does not kill — and it
	 * dealt damage, since something reduced health to zero. The magnitude is unknown and unused: the table
	 * only asks whether damage was positive.
	 *
	 * @param directIsOwner whether the <em>killing blow</em> came directly from Todo. Read it from the
	 *     victim's last damage source; an arrow he fired has the arrow as its direct entity, so a ranged
	 *     kill answers {@code false} and leaves the melee window alone.
	 */
	public static Spend decideOnKill(
			boolean reentrantBonus,
			boolean directIsOwner,
			boolean isTodoVessel,
			boolean attackerPlayable,
			boolean hasMomentum
	) {
		return decide(false, DAMAGE_LANDED, reentrantBonus, directIsOwner, isTodoVessel, attackerPlayable, hasMomentum);
	}

	/** Any positive value: the table tests the sign, and a kill's magnitude is neither known nor asked for. */
	private static final float DAMAGE_LANDED = 1.0f;
}
