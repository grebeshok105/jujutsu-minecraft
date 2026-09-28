package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jujutsu.mod.character.megumi.MegumiTigerPolicy.ComboAction;
import jujutsu.mod.character.megumi.MegumiTigerPolicy.ComboFacts;
import jujutsu.mod.character.megumi.MegumiTigerPolicy.Strike;
import org.junit.jupiter.api.Test;

class MegumiTigerPolicyTest {

	// --- the combo's numbered tables (literal pins, §B/§C) ---

	@Test
	void theThreeBeatsCarryThe5_7_12DamageCurve() {
		assertEquals(5.0, MegumiTigerPolicy.damageFor(Strike.STRIKE_1), 1.0E-9);
		assertEquals(7.0, MegumiTigerPolicy.damageFor(Strike.STRIKE_2), 1.0E-9);
		assertEquals(12.0, MegumiTigerPolicy.damageFor(Strike.FINISHER), 1.0E-9);
	}

	@Test
	void theBeatsResolveOnTheirOwnClocks() {
		assertEquals(6, MegumiTigerPolicy.resolveTicksFor(Strike.STRIKE_1));
		assertEquals(8, MegumiTigerPolicy.resolveTicksFor(Strike.STRIKE_2));
		assertEquals(12, MegumiTigerPolicy.resolveTicksFor(Strike.FINISHER));
		assertEquals(12, MegumiTigerPolicy.windupTicksFor(), "the windup telegraph");
		assertEquals(30, MegumiTigerPolicy.recoveryTicks(), "the commit's price");
		assertEquals(160, MegumiTigerPolicy.comboCooldownTicks(), "the next window");
	}

	@Test
	void theBeatRangesGrowIntoTheFinisher() {
		assertEquals(2.6, MegumiTigerPolicy.rangeFor(Strike.STRIKE_1), 1.0E-9);
		assertEquals(2.8, MegumiTigerPolicy.rangeFor(Strike.STRIKE_2), 1.0E-9);
		assertEquals(3.0, MegumiTigerPolicy.rangeFor(Strike.FINISHER), 1.0E-9);
	}

	@Test
	void theBeatArcsWidenIntoTheFinisher() {
		assertEquals(70.0, MegumiTigerPolicy.arcDegFor(Strike.STRIKE_1), 1.0E-9);
		assertEquals(70.0, MegumiTigerPolicy.arcDegFor(Strike.STRIKE_2), 1.0E-9);
		assertEquals(80.0, MegumiTigerPolicy.arcDegFor(Strike.FINISHER), 1.0E-9);
	}

	@Test
	void theStaggerBuildsAcrossTheCombo() {
		assertEquals(6, MegumiTigerPolicy.staggerTicksFor(Strike.STRIKE_1));
		assertEquals(8, MegumiTigerPolicy.staggerTicksFor(Strike.STRIKE_2));
		assertEquals(20, MegumiTigerPolicy.staggerTicksFor(Strike.FINISHER));
	}

	@Test
	void onlyTheFinisherLaunches() {
		assertEquals(0.0, MegumiTigerPolicy.liftFor(Strike.STRIKE_1), 1.0E-9);
		assertEquals(0.0, MegumiTigerPolicy.liftFor(Strike.STRIKE_2), 1.0E-9);
		assertEquals(0.25, MegumiTigerPolicy.liftFor(Strike.FINISHER), 1.0E-9);
		assertEquals(1.2, MegumiTigerPolicy.knockbackFor(Strike.FINISHER), 1.0E-9);
	}

	// --- beat transition table: hit / miss / expire, per strike ---

	@Test
	void aConnectAlwaysAdvancesToTheNextBeat() {
		assertEquals(ComboAction.NEXT_BEAT, MegumiTigerPolicy.afterBeat(Strike.STRIKE_1, true, true));
		assertEquals(ComboAction.NEXT_BEAT, MegumiTigerPolicy.afterBeat(Strike.STRIKE_2, true, true));
	}

	@Test
	void aMissOnAPresentTargetKeepsTheCommittedSwingsComing() {
		assertEquals(ComboAction.NEXT_BEAT, MegumiTigerPolicy.afterBeat(Strike.STRIKE_1, false, true),
				"strike_1 whiffs audibly and the sequence still runs");
		assertEquals(ComboAction.NEXT_BEAT, MegumiTigerPolicy.afterBeat(Strike.STRIKE_2, false, true),
				"strike_2 whiffs audibly and the finisher still launches");
	}

	@Test
	void aMissOnAGoneTargetCollapsesTheCombo() {
		assertEquals(ComboAction.RECOVER, MegumiTigerPolicy.afterBeat(Strike.STRIKE_1, false, false),
				"dead or despawned mid-combo — nothing left to swing at");
		assertEquals(ComboAction.RECOVER, MegumiTigerPolicy.afterBeat(Strike.STRIKE_2, false, false));
	}

	@Test
	void theFinisherAlwaysEndsTheSequence() {
		assertEquals(ComboAction.RECOVER, MegumiTigerPolicy.afterBeat(Strike.FINISHER, true, true));
		assertEquals(ComboAction.RECOVER, MegumiTigerPolicy.afterBeat(Strike.FINISHER, false, true));
		assertEquals(ComboAction.RECOVER, MegumiTigerPolicy.afterBeat(Strike.FINISHER, false, false));
		assertNull(Strike.FINISHER.next(), "no fourth beat exists");
	}

	@Test
	void theBeatOrderIsStrike1Strike2Finisher() {
		assertEquals(Strike.STRIKE_2, Strike.STRIKE_1.next());
		assertEquals(Strike.FINISHER, Strike.STRIKE_2.next());
		assertEquals(1, Strike.STRIKE_1.beat());
		assertEquals(2, Strike.STRIKE_2.beat());
		assertEquals(3, Strike.FINISHER.beat());
	}

	// --- arc geometry ---

	@Test
	void theArcIsSymmetricAroundTheFrozenFacing() {
		assertTrue(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 0.0));
		assertTrue(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 34.9));
		assertTrue(MegumiTigerPolicy.inArc(Strike.STRIKE_1, -34.9));
		assertTrue(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 35.0), "the half-angle edge counts");
		assertFalse(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 35.1));
		assertFalse(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 90.0), "beside the tiger is out");
		assertFalse(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 179.9), "behind is out");
	}

	@Test
	void theFinisherReachesWiderThanTheStrikes() {
		assertTrue(MegumiTigerPolicy.inArc(Strike.FINISHER, 39.9));
		assertFalse(MegumiTigerPolicy.inArc(Strike.FINISHER, 40.1));
		assertFalse(MegumiTigerPolicy.inArc(Strike.STRIKE_1, 39.9),
				"inside the finisher's band but outside the strike's");
	}

	// --- the connect matrix: range AND arc AND live ---

	@Test
	void aTargetInReachInsideTheArcConnects() {
		assertTrue(MegumiTigerPolicy.strikeConnects(new ComboFacts(Strike.STRIKE_1, true, true, 2.0, 0.0)));
		assertTrue(MegumiTigerPolicy.strikeConnects(
				new ComboFacts(Strike.STRIKE_1, true, true, 2.6, 35.0)),
				"the far edge of both bands at once still counts");
	}

	@Test
	void aTargetPastTheReachWhiffs() {
		assertFalse(MegumiTigerPolicy.strikeConnects(
				new ComboFacts(Strike.STRIKE_1, true, true, 2.61, 0.0)),
				"one centimetre past strike_1's reach is a miss");
		assertFalse(MegumiTigerPolicy.strikeConnects(
				new ComboFacts(Strike.FINISHER, true, true, 3.01, 0.0)),
				"past the finisher's reach too");
	}

	@Test
	void aTargetOutsideTheArcWhiffs() {
		assertFalse(MegumiTigerPolicy.strikeConnects(
				new ComboFacts(Strike.STRIKE_1, true, true, 1.0, 36.0)),
				"in reach but slid out of the frozen facing's band");
		assertFalse(MegumiTigerPolicy.strikeConnects(
				new ComboFacts(Strike.FINISHER, true, true, 1.0, 41.0)));
	}

	@Test
	void aDeadOrWrongLevelTargetNeverConnects() {
		assertFalse(MegumiTigerPolicy.strikeConnects(new ComboFacts(Strike.STRIKE_1, false, true, 1.0, 0.0)),
				"the lock holds the identity, not the ghost");
		assertFalse(MegumiTigerPolicy.strikeConnects(new ComboFacts(Strike.STRIKE_1, true, false, 1.0, 0.0)),
				"another dimension is not reach");
	}

	// --- the pin that makes the combo committed ---

	@Test
	void theComboNeverRetargetsMidSequence() {
		assertFalse(MegumiTigerPolicy.retargetDuringCombo(),
				"pinned by §C: the locked identity carries the whole sequence");
	}
}
