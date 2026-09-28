package jujutsu.mod.character.nobara.projectjjk;

/**
 * Server-side preflight for the strong Straw Doll connection.
 *
 * <p>Connection-source matrix:
 * <table>
 *   <tr><th>source</th><th>consumer</th><th>strength</th></tr>
 *   <tr><td>bound remnant</td><td>doll ritual</td><td>strong, target UUID + dimension</td></tr>
 *   <tr><td>curse-link</td><td>self resonance</td><td>unchanged, explicit link</td></tr>
 *   <tr><td>plain embedded nail</td><td>neither</td><td>marks/setup only</td></tr>
 * </table>
 *
 * <p>A remnant target is resolved by UUID only after its stored dimension matches the caster's
 * current server level. Loaded-entity resolution is the range gate: no distance threshold is used.
 */
public final class ResonancePolicy {
	private ResonancePolicy() {}

	public static Validation validate(
			boolean hasDoll,
			boolean hasRemnant,
			boolean hasNail,
			boolean sameDimension,
			boolean targetValid,
			boolean alreadyCasting
	) {
		if (alreadyCasting) {
			return Validation.ALREADY_CASTING;
		}
		if (!hasDoll) {
			return Validation.NO_DOLL;
		}
		if (!hasRemnant) {
			return Validation.NO_REMNANT;
		}
		if (!hasNail) {
			return Validation.NO_NAIL;
		}
		if (!sameDimension) {
			return Validation.WRONG_DIMENSION;
		}
		if (!targetValid) {
			return Validation.TARGET_INVALID;
		}
		return Validation.OK;
	}

	public static boolean shouldConsume(Validation validation) {
		return validation == Validation.OK;
	}

	public enum Validation {
		ALREADY_CASTING,
		NO_DOLL,
		NO_REMNANT,
		NO_NAIL,
		WRONG_DIMENSION,
		TARGET_INVALID,
		OK
	}
}
