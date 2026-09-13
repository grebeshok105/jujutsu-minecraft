package jujutsu.mod.cursedspirit;

import java.util.List;
import java.util.Optional;

/**
 * The cursed-spirit power axis (issue #81). Grade 5 is the weakest, grade 1 the
 * strongest; {@link #powerRank()} is the only legal comparison direction
 * (rank 1 = weakest), never the raw 5→1 level number.
 *
 * <p>v1 spawns only {@link #SPAWNABLE_V1} (grades 5/4/3). Grades 2/1 exist in
 * the model so later content does not require a rewrite, but they carry no
 * balance content: every band lookup on them throws.
 */
public enum CursedSpiritGrade {
	GRADE_5(5),
	GRADE_4(4),
	GRADE_3(3),
	GRADE_2(2),
	GRADE_1(1);

	/** Grades with v1 spawn content, weakest first. */
	public static final List<CursedSpiritGrade> SPAWNABLE_V1 = List.of(GRADE_5, GRADE_4, GRADE_3);

	private final int level;

	CursedSpiritGrade(int level) {
		this.level = level;
	}

	/** The 5→1 level number. Identity only — never arithmetic, never comparison. */
	public int level() {
		return level;
	}

	/**
	 * Strength rank, 1 = weakest ({@code GRADE_5}) … 5 = strongest
	 * ({@code GRADE_1}). All strength comparisons go through this, never
	 * through {@link #level()}.
	 */
	public int powerRank() {
		return 6 - level;
	}

	/** Lookup by 5→1 level; empty for anything outside 1..5. */
	public static Optional<CursedSpiritGrade> byLevel(int level) {
		for (CursedSpiritGrade grade : values()) {
			if (grade.level == level) {
				return Optional.of(grade);
			}
		}
		return Optional.empty();
	}
}
