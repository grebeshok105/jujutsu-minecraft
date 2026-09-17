package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The roster's availability rules, tested without a server: the per-type cooldown ledger and the
 * availability-aware cycle.
 *
 * <p>These two are the server half of "the short press moves to the next <em>available</em>
 * shikigami". The gesture test in the input block proves the press reaches this path; this file
 * proves the path then refuses a recovering type — that a cooled entry is skipped rather than
 * selected, and that a roster with nothing available keeps the current selection instead of walking
 * onto something the player cannot use.
 *
 * <p>The ledger's clock is fed per server tick in production and reached here through its documented
 * seam, so the arithmetic below is exact: a deadline is stamped at {@code observe}'s value plus the
 * duration, and every assertion measures against an explicit {@code now}.
 */
class MegumiShikigamiAvailabilityTest {
	private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final long NOW = 1_000L;

	@AfterEach
	void clearStaticState() {
		MegumiShikigamiCooldowns.clearAll();
		MegumiShikigamiSelection.clearAll();
	}

	@Test
	void aTypeWithNoLedgerEntryIsReady() {
		assertFalse(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.NUE, NOW),
				"a type nobody charged must be selectable");
		assertEquals(0, MegumiShikigamiCooldowns.remainingTicks(ALICE, MegumiShikigami.NUE, NOW));
	}

	@Test
	void aStartedCooldownCoolsExactlyItsDurationAndThenExpires() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.NUE, 40);

		assertTrue(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.NUE, NOW),
				"the tick that charged it is still cooling");
		assertEquals(40, MegumiShikigamiCooldowns.remainingTicks(ALICE, MegumiShikigami.NUE, NOW));
		assertTrue(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.NUE, NOW + 39),
				"one tick short of the deadline is still cooling");
		assertFalse(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.NUE, NOW + 40),
				"the deadline itself is ready — the ledger's ready time is exclusive");
		assertEquals(0, MegumiShikigamiCooldowns.remainingTicks(ALICE, MegumiShikigami.NUE, NOW + 40));
	}

	@Test
	void chargingWhileCoolingKeepsTheLongerDeadline() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.NUE, 240);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.NUE, 40);

		assertEquals(240, MegumiShikigamiCooldowns.remainingTicks(ALICE, MegumiShikigami.NUE, NOW),
				"a shorter charge must not cut a cooldown the player is still paying");
	}

	@Test
	void theLedgerIsPerOwnerAndPerType() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.RABBITS, 100);

		assertTrue(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.RABBITS, NOW));
		assertFalse(MegumiShikigamiCooldowns.isCooling(BOB, MegumiShikigami.RABBITS, NOW),
				"one owner's recall must not cool another owner's type");
		assertFalse(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.ELEPHANT, NOW),
				"one type's recall must not cool the rest of the roster");

		MegumiShikigamiCooldowns.clear(ALICE);
		assertFalse(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.RABBITS, NOW),
				"a disconnect or vessel change drops the whole ledger for that owner");
	}

	@Test
	void clearAllEmptiesEveryOwner() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.DOGS, 100);
		MegumiShikigamiCooldowns.start(BOB, MegumiShikigami.TOAD, 100);
		MegumiShikigamiCooldowns.clearAll();

		assertFalse(MegumiShikigamiCooldowns.isCooling(ALICE, MegumiShikigami.DOGS, NOW));
		assertFalse(MegumiShikigamiCooldowns.isCooling(BOB, MegumiShikigami.TOAD, NOW));
	}

	@Test
	void cyclingSkipsACoolingTypeAndLandsOnTheNextUsableOne() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiSelection.set(ALICE, MegumiShikigami.DOGS);
		MegumiShikigamiCooldowns.start(ALICE, MegumiShikigami.NUE, 100);

		MegumiShikigami landed = MegumiShikigamiSelection.cycleAvailable(ALICE,
				type -> !MegumiShikigamiCooldowns.isCooling(ALICE, type, NOW));

		assertEquals(MegumiShikigami.TOAD, landed, "a cooling entry must be stepped over, not selected");
		assertEquals(MegumiShikigami.TOAD, MegumiShikigamiSelection.selected(ALICE),
				"the skip must be stored, not merely returned");
	}

	@Test
	void cyclingKeepsEverythingWhenNothingIsUsable() {
		MegumiShikigamiCooldowns.observe(NOW);
		MegumiShikigamiSelection.set(ALICE, MegumiShikigami.ELEPHANT);
		for (MegumiShikigami type : MegumiShikigami.values()) {
			MegumiShikigamiCooldowns.start(ALICE, type, 100);
		}

		MegumiShikigami landed = MegumiShikigamiSelection.cycleAvailable(ALICE,
				type -> !MegumiShikigamiCooldowns.isCooling(ALICE, type, NOW));

		assertEquals(MegumiShikigami.ELEPHANT, landed,
				"with the whole roster recovering the selection stays where it was");
		assertEquals(MegumiShikigami.ELEPHANT, MegumiShikigamiSelection.selected(ALICE));
	}

	@Test
	void theAvailabilityAwareCycleWalksTheSameCanonicalOrder() {
		// One order for the cycle and for the strip: with nothing cooling, this is the plain cycle.
		MegumiShikigamiSelection.set(ALICE, MegumiShikigami.DOGS);
		MegumiShikigami[] expected = { MegumiShikigami.NUE, MegumiShikigami.TOAD, MegumiShikigami.RABBITS,
				MegumiShikigami.ELEPHANT, MegumiShikigami.DOGS };
		for (MegumiShikigami step : expected) {
			assertEquals(step, MegumiShikigamiSelection.cycleAvailable(ALICE, type -> true),
					"the availability-aware cycle must share the roster's own order");
		}
	}
}
