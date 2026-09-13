package jujutsu.mod.cursedspirit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityBrain;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityId;
import jujutsu.mod.cursedspirit.ability.CursedSpiritAbilityProfile;

/**
 * R50 coexistence: REGEN lives alongside DASH and survives its end. Entity-free — windows
 * open through the same {@code tryStart} seam the effects use, and expiry is the clock
 * passing the window, never {@code forceEnd}.
 *
 * <p>Red-proof: assert the dash inactive at tick 101 (or regen inactive at 115) and both
 * tests go red — the starts genuinely overlap.
 */
final class CursedSpiritAbilityCoexistenceTest {
	private static final List<CursedSpiritAbilityId> TRIO = List.of(
			CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.DASH,
			CursedSpiritAbilityId.ARMOR);

	private static CursedSpiritAbilityBrain pinned() {
		CursedSpiritAbilityBrain brain = new CursedSpiritAbilityBrain();
		brain.forcePoolForTest(TRIO);
		return brain;
	}

	private static void startRegenThenDash(CursedSpiritAbilityBrain brain) {
		assertTrue(brain.tryStart(CursedSpiritAbilityId.REGEN, 220,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.REGEN,
						CursedSpiritGrade.GRADE_5),
				null, 100), "regen starts");
		// A tick later: the per-tick start cap holds within a tick, never across ticks,
		// and regen occupies neither the movement group nor the attack clip.
		assertTrue(brain.tryStart(CursedSpiritAbilityId.DASH, 115,
				CursedSpiritAbilityProfile.of(CursedSpiritAbilityId.DASH,
						CursedSpiritGrade.GRADE_5),
				UUID.randomUUID(), 101), "dash starts alongside regen");
	}

	@Test
	void regenAndDashStayActiveTogether() {
		CursedSpiritAbilityBrain brain = pinned();
		startRegenThenDash(brain);
		assertTrue(brain.isActive(CursedSpiritAbilityId.REGEN, 101), "regen active");
		assertTrue(brain.isActive(CursedSpiritAbilityId.DASH, 101), "dash active");
		assertEquals(Set.of(CursedSpiritAbilityId.REGEN, CursedSpiritAbilityId.DASH),
				brain.activeIds(101), "both windows open");
	}

	@Test
	void dashExpiryLeavesRegenAlive() {
		CursedSpiritAbilityBrain brain = pinned();
		startRegenThenDash(brain);
		// Dash (GRADE_5 duration 14, started at 101) is open at 114 and shut at 115.
		assertTrue(brain.isActive(CursedSpiritAbilityId.DASH, 114), "dash open at 114");
		assertFalse(brain.isActive(CursedSpiritAbilityId.DASH, 115), "dash shut at 115");
		assertTrue(brain.isActive(CursedSpiritAbilityId.REGEN, 115), "regen outlives dash");
		assertEquals(Set.of(CursedSpiritAbilityId.REGEN), brain.activeIds(115),
				"only regen remains");
	}
}
