package jujutsu.mod.character.nobara.projectjjk;

public final class ProjectJjkRitualPolicyTest {
	private ProjectJjkRitualPolicyTest() {}

	public static void main(String[] args) {
		assertValidationOrder();
		assertRangeBoundary();
		assertConsumptionDecision();
		System.out.println("ProjectJjkRitualPolicyTest passed");
	}

	private static void assertValidationOrder() {
		assert validate(false, true, true, true, true, 1.0, false) == ProjectJjkRitualPolicy.Validation.NO_DOLL;
		assert validate(true, false, true, true, true, 1.0, false) == ProjectJjkRitualPolicy.Validation.NO_REMNANT;
		assert validate(true, true, false, true, true, 1.0, false) == ProjectJjkRitualPolicy.Validation.NO_NAIL;
		assert validate(true, true, true, false, true, 1.0, false) == ProjectJjkRitualPolicy.Validation.TARGET_INVALID;
		assert validate(true, true, true, true, false, 1.0, false) == ProjectJjkRitualPolicy.Validation.WRONG_DIMENSION;
		assert validate(true, true, true, true, true, 1.0, true) == ProjectJjkRitualPolicy.Validation.ALREADY_CASTING;
	}

	private static void assertRangeBoundary() {
		assert validate(true, true, true, true, true, 63.9, false) == ProjectJjkRitualPolicy.Validation.OK;
		assert validate(true, true, true, true, true, 64.0, false) == ProjectJjkRitualPolicy.Validation.OK;
		assert validate(true, true, true, true, true, 64.1, false) == ProjectJjkRitualPolicy.Validation.OUT_OF_RANGE;
	}

	private static void assertConsumptionDecision() {
		for (ProjectJjkRitualPolicy.Validation validation : ProjectJjkRitualPolicy.Validation.values()) {
			assert ProjectJjkRitualPolicy.shouldConsume(validation) == (validation == ProjectJjkRitualPolicy.Validation.OK);
		}
	}

	private static ProjectJjkRitualPolicy.Validation validate(
			boolean hasDoll,
			boolean hasRemnant,
			boolean hasNail,
			boolean targetValid,
			boolean sameDimension,
			double distance,
			boolean alreadyCasting
	) {
		return ProjectJjkRitualPolicy.validate(
				hasDoll,
				hasRemnant,
				hasNail,
				targetValid,
				sameDimension,
				distance,
				alreadyCasting
		);
	}
}
