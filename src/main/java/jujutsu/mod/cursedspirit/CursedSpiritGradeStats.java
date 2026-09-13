package jujutsu.mod.cursedspirit;

/**
 * One individual's rolled finals: the values {@code applyStats} writes into
 * the {@code MAX_HEALTH}/{@code ATTACK_DAMAGE}/{@code MOVEMENT_SPEED}
 * attributes and into NBT. Always inside the owner's grade band, for every
 * tier — that is the #81 invariant, on these numbers.
 */
public record CursedSpiritGradeStats(double maxHealth, double attackDamage, double movementSpeed) {
	/** Whether every stat sits inside the given grade's band (edges count). */
	public boolean inBandsOf(CursedSpiritGrade grade) {
		return CursedSpiritGradeProfile.health(grade).contains(maxHealth)
				&& CursedSpiritGradeProfile.damage(grade).contains(attackDamage)
				&& CursedSpiritGradeProfile.speed(grade).contains(movementSpeed);
	}
}
