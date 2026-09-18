package jujutsu.mod.cursedincident.policy;

/** Pure grade-to-parameter derivations shared by incident rolls and tests. */
public final class TemplateRollPolicy {
	public static final int MIN_GRADE = 1;
	public static final int MAX_GRADE = 5;
	/** Only one dependent centre may exist; self-sustaining secondaries do not count. */
	public static final int MAX_DEPENDENT_CENTERS = 1;

	private TemplateRollPolicy() {
	}

	/** Stronger grades (lower number) produce larger zones. */
	public static double maxRadiusMul(int grade) {
		return switch (clampGrade(grade)) {
			case 1 -> 1.40;
			case 2 -> 1.20;
			case 3 -> 1.00;
			case 4 -> 0.85;
			default -> 0.70;
		};
	}

	/** Lower grade number is stronger and therefore crosses age thresholds sooner. */
	public static double escalationSpeedMul(int grade) {
		return switch (clampGrade(grade)) {
			case 1 -> 0.70;
			case 2 -> 0.85;
			case 3 -> 1.00;
			case 4 -> 1.20;
			default -> 1.40;
		};
	}

	/** Multiplier used when biasing a template's attracted curse-tier weights. */
	public static double curseTierBias(int grade) {
		return switch (clampGrade(grade)) {
			case 1 -> 1.60;
			case 2 -> 1.30;
			case 3 -> 1.00;
			case 4 -> 0.80;
			default -> 0.60;
		};
	}

	/** Minimum seal tier required for this grade (1..3). */
	public static int sealDifficulty(int grade) {
		return switch (clampGrade(grade)) {
			case 1, 2 -> 3;
			case 3, 4 -> 2;
			default -> 1;
		};
	}

	/** Chance for a grade-specific anomaly during a seeded roll. */
	public static double anomalyChance(int grade) {
		return switch (clampGrade(grade)) {
			case 1 -> 0.35;
			case 2 -> 0.25;
			case 3 -> 0.15;
			case 4 -> 0.08;
			default -> 0.03;
		};
	}

	/** Relative resistance to infection work; stronger grades take more work. */
	public static double resilience(int grade) {
		return switch (clampGrade(grade)) {
			case 1 -> 1.50;
			case 2 -> 1.25;
			case 3 -> 1.00;
			case 4 -> 0.85;
			default -> 0.70;
		};
	}

	public static int clampGrade(int grade) {
		return Math.max(MIN_GRADE, Math.min(MAX_GRADE, grade));
	}
}
