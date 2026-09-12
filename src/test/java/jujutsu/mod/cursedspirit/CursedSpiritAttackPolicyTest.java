package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy.Body;
import jujutsu.mod.cursedspirit.CursedSpiritAttackPolicy.Phase;
import org.junit.jupiter.api.Test;

/**
 * Attack policy math (Step 9): phase transitions, windup/reach/cooldown boundaries, and the
 * entity-free AoE membership core.
 */
final class CursedSpiritAttackPolicyTest {
	private static final CursedSpiritTierStats COMMON = CursedSpiritProfile.of(CursedSpiritTier.COMMON);

	@Test
	void approachEntersWindupOnlyInReach() {
		assertEquals(Phase.WINDUP, CursedSpiritAttackPolicy.advance(Phase.APPROACH, 3, true, COMMON));
		assertEquals(Phase.APPROACH, CursedSpiritAttackPolicy.advance(Phase.APPROACH, 3, false, COMMON));
	}

	@Test
	void windupStrikesExactlyAtWindupTicks() {
		int windup = COMMON.attackWindupTicks();
		assertFalse(CursedSpiritAttackPolicy.windupDone(windup - 1, COMMON));
		assertTrue(CursedSpiritAttackPolicy.windupDone(windup, COMMON));
		assertEquals(Phase.WINDUP, CursedSpiritAttackPolicy.advance(Phase.WINDUP, windup - 1, true, COMMON));
		assertEquals(Phase.STRIKE, CursedSpiritAttackPolicy.advance(Phase.WINDUP, windup, true, COMMON));
	}

	@Test
	void strikeAlwaysRecoversAndRecoverCoolsDown() {
		assertEquals(Phase.RECOVER, CursedSpiritAttackPolicy.advance(Phase.STRIKE, 0, true, COMMON));
		int cooldown = COMMON.attackCooldownTicks();
		assertEquals(Phase.RECOVER, CursedSpiritAttackPolicy.advance(Phase.RECOVER, cooldown - 1, true, COMMON));
		assertEquals(Phase.APPROACH, CursedSpiritAttackPolicy.advance(Phase.RECOVER, cooldown, true, COMMON));
	}

	@Test
	void inReachBoundaryUsesBothHalfWidths() {
		// COMMON reach 2.5 + attacker half 0.375 + victim half 0.45 = 3.325.
		assertTrue(CursedSpiritAttackPolicy.inReach(3.325, 0.75, 0.9, COMMON));
		assertFalse(CursedSpiritAttackPolicy.inReach(3.326, 0.75, 0.9, COMMON));
		assertTrue(CursedSpiritAttackPolicy.inReach(0.0, 0.75, 0.9, COMMON));
	}

	@Test
	void aoeMembershipKeepsInsideAliveNonPrimary() {
		CursedSpiritTierStats greater = CursedSpiritProfile.of(CursedSpiritTier.GREATER);
		// Index 0 = primary (inside radius but excluded), 1 = inside, 2 = outside, 3 = inside but dead.
		List<Body> bodies = List.of(
				new Body(1.0, 0.0, 0.0, true),
				new Body(2.0, 0.0, 0.0, true),
				new Body(10.0, 0.0, 0.0, true),
				new Body(1.5, 0.0, 0.0, false));
		assertEquals(List.of(1),
				CursedSpiritAttackPolicy.aoeMemberIndices(bodies, 0, 0.0, 0.0, 0.0, greater));
	}

	@Test
	void zeroRadiusTiersSplashNobody() {
		List<Body> bodies = List.of(
				new Body(1.0, 0.0, 0.0, true),
				new Body(0.5, 0.0, 0.0, true));
		assertTrue(CursedSpiritAttackPolicy.aoeMemberIndices(bodies, 0, 0.0, 0.0, 0.0, COMMON).isEmpty());
		assertTrue(CursedSpiritAttackPolicy.aoeMemberIndices(
				bodies, 0, 0.0, 0.0, 0.0, CursedSpiritProfile.of(CursedSpiritTier.LESSER)).isEmpty());
	}

	@Test
	void cooldownAndDamageReadTheRow() {
		assertEquals(COMMON.attackCooldownTicks(), CursedSpiritAttackPolicy.cooldownTicks(COMMON));
		assertEquals((float) COMMON.attackDamage(), CursedSpiritAttackPolicy.primaryDamage(COMMON));
	}
}
