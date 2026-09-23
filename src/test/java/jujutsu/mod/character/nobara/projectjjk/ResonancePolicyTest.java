package jujutsu.mod.character.nobara.projectjjk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class ResonancePolicyTest {
	private ResonancePolicyTest() {}

	public static void main(String[] args) {
		assertValidationOrder();
		assertLoadedEntityGateHasNoRangeCutoff();
		assertConsumptionDecision();
		assertConnectionSourceMatrixIsDocumented();
		System.out.println("ResonancePolicyTest passed");
	}

	private static void assertValidationOrder() {
		assert validate(false, true, true, true, true, false) == ResonancePolicy.Validation.NO_DOLL;
		assert validate(true, false, true, true, true, false) == ResonancePolicy.Validation.NO_REMNANT;
		assert validate(true, true, false, true, true, false) == ResonancePolicy.Validation.NO_NAIL;
		assert validate(true, true, true, false, true, false) == ResonancePolicy.Validation.WRONG_DIMENSION;
		assert validate(true, true, true, true, false, false) == ResonancePolicy.Validation.TARGET_INVALID;
		assert validate(true, true, true, true, true, true) == ResonancePolicy.Validation.ALREADY_CASTING;
		assert ResonancePolicy.Validation.values().length == 7 : "validation matrix must stay explicit";
	}

	private static void assertLoadedEntityGateHasNoRangeCutoff() {
		assert validate(true, true, true, true, true, false) == ResonancePolicy.Validation.OK
				: "a loaded same-dimension target is valid regardless of distance";
		String source = read("src/main/java/jujutsu/mod/character/nobara/projectjjk/ResonancePolicy.java");
		assert !source.contains("MAX_RANGE") : "range constant must be removed";
		assert !source.contains("OUT_OF_RANGE") : "range validation must be removed";
		assert !source.contains("distance >") : "distance must not gate a loaded entity";
	}

	private static void assertConsumptionDecision() {
		for (ResonancePolicy.Validation validation : ResonancePolicy.Validation.values()) {
			assert ResonancePolicy.shouldConsume(validation)
					== (validation == ResonancePolicy.Validation.OK);
		}
	}

	private static void assertConnectionSourceMatrixIsDocumented() {
		String source = read("src/main/java/jujutsu/mod/character/nobara/projectjjk/ResonancePolicy.java");
		for (String term : Set.of("bound remnant", "doll ritual", "curse-link", "self resonance",
				"plain embedded nail", "neither")) {
			assert source.contains(term) : "connection matrix lost: " + term;
		}
	}

	private static ResonancePolicy.Validation validate(
			boolean hasDoll,
			boolean hasRemnant,
			boolean hasNail,
			boolean sameDimension,
			boolean targetValid,
			boolean alreadyCasting
	) {
		return ResonancePolicy.validate(
				hasDoll,
				hasRemnant,
				hasNail,
				sameDimension,
				targetValid,
				alreadyCasting
		);
	}

	private static String read(String relativePath) {
		try {
			return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
		} catch (Exception exception) {
			throw new AssertionError(relativePath, exception);
		}
	}
}
