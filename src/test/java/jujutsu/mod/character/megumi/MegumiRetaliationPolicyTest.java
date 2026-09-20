package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The pure half of the retaliation picker (issue #76). The entity-shaped half — nearest-wins among
 * two live aggressors, and the manual sic outranking this pass — is behavioural and lives in
 * {@code MegumiShikigamiRetaliationGameTests}.
 */
class MegumiRetaliationPolicyTest {
	private static final long WINDOW = MegumiProfile.RETALIATION_WINDOW_TICKS;

	@Test
	void anAttackerStaysFreshThroughTheWindowAndExpiresRightAfter() {
		assertTrue(MegumiRetaliationPolicy.attackerFresh(1_000L, 1_000L, WINDOW), "hit this tick is fresh");
		assertTrue(MegumiRetaliationPolicy.attackerFresh(1_000L, 1_000L - WINDOW, WINDOW),
				"exactly window ticks old is still fresh");
		assertFalse(MegumiRetaliationPolicy.attackerFresh(1_000L, 1_000L - WINDOW - 1, WINDOW),
				"one tick past the window expires");
	}

	@Test
	void aBodyWhichIsGoneIsNeverUsable() {
		assertFalse(MegumiRetaliationPolicy.isUsable(null));
	}

	@Test
	void anEmptyAggressorListYieldsNothing() {
		assertEquals(null, MegumiRetaliationPolicy.nearestAggressor(null, List.of()));
	}

	/**
	 * Issue #96/#107 — only the mark this pass placed expires the tick no aggressor answers. The
	 * owner's manual sic is an order, and the coordinator's autonomous mark belongs to the
	 * coordination pass; neither is this pass's to take away.
	 */
	@Test
	void onlyThePacksOwnMarkExpiresWithoutAnAggressor() {
		assertTrue(MegumiRetaliationPolicy.markExpiresWithoutAggressor(MegumiMarkKind.RETALIATION),
				"a retaliation mark dies with the window");
		assertFalse(MegumiRetaliationPolicy.markExpiresWithoutAggressor(MegumiMarkKind.MANUAL),
				"a manual sic outlives the window");
		assertFalse(MegumiRetaliationPolicy.markExpiresWithoutAggressor(MegumiMarkKind.AUTONOMOUS),
				"an autonomous mark is dropped by the coordinator, not by this pass");
	}
}
