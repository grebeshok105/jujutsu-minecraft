package jujutsu.mod.cursedspirit;

/**
 * One row of per-tier balance numbers. All fields are read by the entity, the attack goal and the
 * spawn integration — tuning happens here, never in logic.
 *
 * @param maxHealth health assigned at attribute registration
 * @param attackDamage direct strike damage
 * @param movementSpeed ground speed
 * @param followRange target acquisition and AI follow range
 * @param knockbackResistance vanilla knockback resistance attribute
 * @param staggerMultiplier scales incoming stagger ticks (floor 1, see the entity)
 * @param attackWindupTicks ticks from WINDUP start to STRIKE
 * @param attackCooldownTicks ticks from STRIKE to the next WINDUP
 * @param attackReach centre-to-centre melee reach bonus added to both bodies' half-widths
 * @param attackKnockback knockback dealt on a direct strike
 * @param strikeStep forward impulse on the attacker at STRIKE (0 disables)
 * @param aoeRadius slam radius around the attacker at STRIKE (0 disables the AoE)
 * @param aoeDamageScale AoE damage as a fraction of {@code attackDamage}
 * @param aoeKnockback knockback dealt to AoE bodies
 * @param xpReward experience dropped on death
 * @param spawnWeight natural-spawn weight row
 * @param spawnMinGroup natural-spawn group minimum
 * @param spawnMaxGroup natural-spawn group maximum
 */
public record CursedSpiritTierStats(
		double maxHealth,
		double attackDamage,
		double movementSpeed,
		double followRange,
		double knockbackResistance,
		double staggerMultiplier,
		int attackWindupTicks,
		int attackCooldownTicks,
		double attackReach,
		double attackKnockback,
		double strikeStep,
		double aoeRadius,
		double aoeDamageScale,
		double aoeKnockback,
		int xpReward,
		int spawnWeight,
		int spawnMinGroup,
		int spawnMaxGroup) {
}
