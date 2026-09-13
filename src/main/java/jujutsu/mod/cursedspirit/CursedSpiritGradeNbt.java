package jujutsu.mod.cursedspirit;

import java.util.Optional;

/**
 * The frozen grade NBT schema (C2). Keys live here — not on the entity — so
 * the schema, its validation and its tests compile and stay green
 * independently of the entity's serialized edit waves.
 *
 * <p>Schema: {@code Grade} (int, 5→1 level), {@code StatHp} /
 * {@code StatDamage} / {@code StatSpeed} (double finals),
 * {@code RollSeed} (long provenance), {@code Variant} (existing key, owned
 * by the entity's variant code). Ability pool lives under {@code Abilities},
 * owned by the ability brain — no key is reserved here.
 */
public final class CursedSpiritGradeNbt {
	public static final String GRADE = "Grade";
	public static final String STAT_HP = "StatHp";
	public static final String STAT_DAMAGE = "StatDamage";
	public static final String STAT_SPEED = "StatSpeed";
	public static final String ROLL_SEED = "RollSeed";

	private CursedSpiritGradeNbt() {}

	/** Whether the level is a known grade slot (1..5, content or not). */
	public static boolean isValidLevel(int level) {
		return CursedSpiritGrade.byLevel(level).isPresent();
	}

	/** Whether the level may live in v1 (spawnable grades 5/4/3 only). */
	public static boolean isSpawnableLevel(int level) {
		Optional<CursedSpiritGrade> grade = CursedSpiritGrade.byLevel(level);
		return grade.isPresent() && CursedSpiritGrade.SPAWNABLE_V1.contains(grade.get());
	}

	/** Whether every final sits inside the claimed grade's band. */
	public static boolean statsInBand(CursedSpiritGradeStats stats, CursedSpiritGrade grade) {
		return stats.inBandsOf(grade);
	}
}
