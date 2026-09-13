package jujutsu.mod.cursedspirit;

/**
 * One row of per-tier balance numbers: morphology (hitbox lives on the type),
 * combat pattern (windup/cooldown/reach/AoE/knockback/stagger), XP and spawn
 * rows.
 *
 * <p>Power stats ({@code MAX_HEALTH}/{@code ATTACK_DAMAGE}/
 * {@code MOVEMENT_SPEED}) are deliberately NOT here: they belong to the
 * grade ({@link CursedSpiritGradeProfile} bands + per-individual
 * {@link CursedSpiritGradeStats}), with the tier contributing only an
 * archetype rank inside the band (D2). All fields are read by the entity,
 * the attack goal and the spawn integration — tuning happens here, never in
 * logic.
 *
 * @param followRange target acquisition and AI follow range
 * @param knockbackResistance vanilla knockback resistance attribute
 * @param staggerMultiplier scales incoming stagger ticks (floor 1, see the entity)
 * @param attackWindupTicks ticks from WINDUP start to STRIKE
 * @param attackCooldownTicks ticks from STRIKE to the next WINDUP
 * @param attackReach centre-to-centre melee reach bonus added to both bodies' half-widths
 * @param attackKnockback knockback dealt on a direct strike
 * @param strikeStep forward impulse on the attacker at STRIKE (0 disables)
 * @param aoeRadius slam radius around the attacker at STRIKE (0 disables the AoE)
 * @param aoeDamageScale AoE damage as a fraction of the rolled grade damage
 * @param aoeKnockback knockback dealt to AoE bodies
 * @param xpReward experience dropped on death
 * @param spawnWeight natural-spawn weight row
 * @param spawnMinGroup natural-spawn group minimum
 * @param spawnMaxGroup natural-spawn group maximum
 */
public record CursedSpiritTierStats(
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
