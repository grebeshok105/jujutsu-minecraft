package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Profile invariants (Step 9): every tier row is sane, spawn weights order T1&gt;T2&gt;T3, groups
 * are well-formed, and the R20 linkage — logic reads the passed row, so a test-side mutated copy
 * moves the derived policy numbers.
 */
final class CursedSpiritProfileTest {
	@Test
	void tierRowsArePositiveAndSane() {
		for (CursedSpiritTier tier : CursedSpiritTier.values()) {
			CursedSpiritTierStats stats = CursedSpiritProfile.of(tier);
			assertTrue(stats.maxHealth() > 0, tier + " maxHealth");
			assertTrue(stats.attackDamage() > 0, tier + " attackDamage");
			assertTrue(stats.movementSpeed() > 0, tier + " movementSpeed");
			assertTrue(stats.followRange() > 0, tier + " followRange");
			assertTrue(stats.attackWindupTicks() > 0, tier + " windup");
			assertTrue(stats.attackCooldownTicks() > stats.attackWindupTicks(),
					tier + " cooldown must exceed windup");
			assertTrue(stats.attackReach() > 0, tier + " reach");
			assertTrue(stats.xpReward() > 0, tier + " xp");
			assertTrue(stats.spawnWeight() > 0, tier + " spawnWeight");
			assertTrue(stats.spawnMinGroup() >= 1, tier + " minGroup >= 1");
			assertTrue(stats.spawnMinGroup() <= stats.spawnMaxGroup(), tier + " minGroup <= maxGroup");
		}
	}

	@Test
	void spawnWeightsOrderLesserAboveCommonAboveGreater() {
		int lesser = CursedSpiritProfile.of(CursedSpiritTier.LESSER).spawnWeight();
		int common = CursedSpiritProfile.of(CursedSpiritTier.COMMON).spawnWeight();
		int greater = CursedSpiritProfile.of(CursedSpiritTier.GREATER).spawnWeight();
		assertTrue(lesser > common, "LESSER weight " + lesser + " must exceed COMMON " + common);
		assertTrue(common > greater, "COMMON weight " + common + " must exceed GREATER " + greater);
	}

	@Test
	void onlyGreaterHasAoeAndOnlyCommonHasStrikeStep() {
		assertEquals(0.0, CursedSpiritProfile.of(CursedSpiritTier.LESSER).aoeRadius());
		assertEquals(0.0, CursedSpiritProfile.of(CursedSpiritTier.COMMON).aoeRadius());
		assertTrue(CursedSpiritProfile.of(CursedSpiritTier.GREATER).aoeRadius() > 0, "GREATER AoE radius");
		assertTrue(CursedSpiritProfile.of(CursedSpiritTier.GREATER).aoeDamageScale() > 0, "GREATER AoE scale");
		assertEquals(0.0, CursedSpiritProfile.of(CursedSpiritTier.LESSER).strikeStep());
		assertTrue(CursedSpiritProfile.of(CursedSpiritTier.COMMON).strikeStep() > 0, "COMMON strikeStep");
		assertEquals(0.0, CursedSpiritProfile.of(CursedSpiritTier.GREATER).strikeStep());
	}

	@Test
	void crowdCapConstants() {
		assertEquals(10, CursedSpiritProfile.MAX_SPIRITS_NEARBY);
		assertEquals(48.0, CursedSpiritProfile.CROWD_RADIUS);
	}

	/**
	 * R20 linkage: mutating a profile row (test-side copy) moves the derived strike numbers, which
	 * proves the goal/policy read the profile instead of literals.
	 */
	@Test
	void mutatedProfileRowMovesDerivedDamage() {
		CursedSpiritTierStats base = CursedSpiritProfile.of(CursedSpiritTier.COMMON);
		CursedSpiritTierStats mutated = new CursedSpiritTierStats(
				base.maxHealth(), base.attackDamage() + 2.0, base.movementSpeed(), base.followRange(),
				base.knockbackResistance(), base.staggerMultiplier(), base.attackWindupTicks(),
				base.attackCooldownTicks(), base.attackReach(), base.attackKnockback(), base.strikeStep(),
				3.0, 0.5, base.aoeKnockback(), base.xpReward(), base.spawnWeight(),
				base.spawnMinGroup(), base.spawnMaxGroup());
		assertEquals((float) base.attackDamage(), CursedSpiritAttackPolicy.primaryDamage(base));
		assertEquals((float) (base.attackDamage() + 2.0), CursedSpiritAttackPolicy.primaryDamage(mutated));
		assertEquals(0.0f, CursedSpiritAttackPolicy.aoeDamage(base));
		assertEquals((float) ((base.attackDamage() + 2.0) * 0.5), CursedSpiritAttackPolicy.aoeDamage(mutated));
		assertEquals(base.attackCooldownTicks(), CursedSpiritAttackPolicy.cooldownTicks(base));
	}
}
