package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkRitualPolicy {
	public static final double MAX_RANGE = 64.0;

	private ProjectJjkRitualPolicy() {}

	public static Validation validate(
			boolean hasDoll,
			boolean hasRemnant,
			boolean hasNail,
			boolean targetValid,
			boolean sameDimension,
			double distance,
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
		if (!Double.isFinite(distance) || distance > MAX_RANGE) {
			return Validation.OUT_OF_RANGE;
		}
		return Validation.OK;
	}

	public static boolean shouldConsume(Validation validation) {
		return validation == Validation.OK;
	}

	public static boolean isSuccessfulOrdinaryHit(boolean damageAccepted, boolean explosiveImpact, boolean selfHit) {
		return damageAccepted && !explosiveImpact && !selfHit;
	}

	public enum Validation {
		OK,
		NO_DOLL,
		NO_REMNANT,
		NO_NAIL,
		TARGET_INVALID,
		WRONG_DIMENSION,
		OUT_OF_RANGE,
		ALREADY_CASTING
	}
}
