package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class MegumiTigerPolicyTest {
	@Test
	void comboStartRequiresAnActiveReadyEligibleTargetInApproachAndStrikeGates() {
		assertTrue(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, true, false, true, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				false, true, true, false, true, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, false, true, false, true, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, false, false, true, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, true, true, true, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, true, false, false, true, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, true, false, true, false, true)));
		assertFalse(MegumiTigerPolicy.canStart(new MegumiTigerPolicy.ComboStartFacts(
				true, true, true, false, true, true, false)));
	}

	@Test
	void targetBoxNearestPointControlsRangeAndForwardArc() {
		Vec3 attacker = Vec3.ZERO;
		Vec3 forward = new Vec3(0.0, 0.0, 1.0);
		AABB broadBox = new AABB(-1.0, 0.0, 2.0, 9.0, 1.0, 5.0);
		assertTrue(MegumiTigerPolicy.inStrikeArc(attacker, forward, broadBox, 3.0, 0.5, 2.0),
				"the closest point is in range even though the box center is not");
		assertFalse(MegumiTigerPolicy.inStrikeArc(attacker, forward,
				new AABB(-0.25, 0.0, -2.0, 0.25, 1.0, -1.0), 3.0, 0.5, 2.0));
		assertFalse(MegumiTigerPolicy.inStrikeArc(attacker, forward,
				new AABB(1.0, 0.0, -0.25, 2.0, 1.0, 0.25), 3.0, 0.5, 2.0));
		assertFalse(MegumiTigerPolicy.inStrikeArc(attacker, forward,
				new AABB(-0.25, 3.0, 1.0, 0.25, 4.0, 2.0), 3.0, 0.5, 2.0));
		assertFalse(MegumiTigerPolicy.inStrikeArc(attacker, Vec3.ZERO,
				new AABB(-0.25, 0.0, 1.0, 0.25, 1.0, 2.0), 3.0, 0.5, 2.0));
	}

	@Test
	void hitWindowsLandAtTheThreeDistinctAuthoredOffsets() {
		int afterWindup = MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK;
		assertFalse(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.COMBO_WINDUP,
				afterWindup + 1));
		assertTrue(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.COMBO_WINDUP, afterWindup));

		int beforeStrikeOne = MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK
				- MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK;
		int beforeStrikeTwo = MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK
				- MegumiShikigamiProfile.TIGER_STRIKE2_WINDOW_TICK;
		assertFalse(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.STRIKE_1,
				beforeStrikeOne + 1));
		assertTrue(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.STRIKE_1, beforeStrikeOne));
		assertFalse(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.STRIKE_2,
				beforeStrikeTwo + 1));
		assertTrue(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.STRIKE_2, beforeStrikeTwo));
		assertFalse(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.FINISHER, 1));
		assertTrue(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.FINISHER, 0));
		assertFalse(MegumiTigerPolicy.hitWindowReached(MegumiTigerPolicy.State.RECOVERY, 0));
	}

	@Test
	void nextStateProgressesEveryBeatAndOnlyRecoveryReturnsToApproach() {
		MegumiTigerPolicy.State state = MegumiTigerPolicy.State.STALK_APPROACH;
		state = MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.COMBO_START);
		assertEquals(MegumiTigerPolicy.State.COMBO_WINDUP, state);
		state = MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.WINDUP_COMPLETE);
		assertEquals(MegumiTigerPolicy.State.STRIKE_1, state);
		state = MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.STRIKE_COMPLETE);
		assertEquals(MegumiTigerPolicy.State.STRIKE_2, state);
		state = MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.STRIKE_COMPLETE);
		assertEquals(MegumiTigerPolicy.State.FINISHER, state);
		state = MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.FINISHER_COMPLETE);
		assertEquals(MegumiTigerPolicy.State.RECOVERY, state);
		assertEquals(MegumiTigerPolicy.State.RECOVERY,
				MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.NEW_TARGET));
		assertEquals(MegumiTigerPolicy.State.STALK_APPROACH,
				MegumiTigerPolicy.nextState(state, MegumiTigerPolicy.Event.RECOVERY_COMPLETE));
	}

	@Test
	void targetMovementAndNewMarksNeverRetargetACommittedBeat() {
		for (MegumiTigerPolicy.State committed : new MegumiTigerPolicy.State[] {
				MegumiTigerPolicy.State.COMBO_WINDUP,
				MegumiTigerPolicy.State.STRIKE_1,
				MegumiTigerPolicy.State.STRIKE_2,
				MegumiTigerPolicy.State.FINISHER
		}) {
			assertEquals(committed,
					MegumiTigerPolicy.nextState(committed, MegumiTigerPolicy.Event.TARGET_MOVED));
			assertEquals(committed,
					MegumiTigerPolicy.nextState(committed, MegumiTigerPolicy.Event.NEW_TARGET));
		}
		for (MegumiTigerPolicy.State committed : new MegumiTigerPolicy.State[] {
				MegumiTigerPolicy.State.COMBO_WINDUP,
				MegumiTigerPolicy.State.STRIKE_1,
				MegumiTigerPolicy.State.STRIKE_2,
				MegumiTigerPolicy.State.FINISHER
		}) {
			assertEquals(MegumiTigerPolicy.State.RECOVERY,
					MegumiTigerPolicy.nextState(committed, MegumiTigerPolicy.Event.TARGET_INVALID));
		}
	}

	@Test
	void outOfArcMissConsumesBeatButInvalidTargetCancelsTheCombo() {
		MegumiTigerPolicy.StrikeFacts miss = new MegumiTigerPolicy.StrikeFacts(true, true, false, true, false);
		assertEquals(MegumiTigerPolicy.StrikeOutcome.MISS, MegumiTigerPolicy.strikeOutcome(miss));
		assertEquals(MegumiTigerPolicy.State.STRIKE_2,
				MegumiTigerPolicy.nextState(MegumiTigerPolicy.State.STRIKE_1,
						MegumiTigerPolicy.Event.STRIKE_COMPLETE));

		assertEquals(MegumiTigerPolicy.StrikeOutcome.CANCEL,
				MegumiTigerPolicy.strikeOutcome(new MegumiTigerPolicy.StrikeFacts(true, false, false, true, true)));
		assertEquals(MegumiTigerPolicy.StrikeOutcome.CANCEL,
				MegumiTigerPolicy.strikeOutcome(new MegumiTigerPolicy.StrikeFacts(true, true, true, true, true)));
		assertEquals(MegumiTigerPolicy.StrikeOutcome.CANCEL,
				MegumiTigerPolicy.strikeOutcome(new MegumiTigerPolicy.StrikeFacts(true, true, false, false, true)));
		assertEquals(MegumiTigerPolicy.StrikeOutcome.CANCEL,
				MegumiTigerPolicy.strikeOutcome(new MegumiTigerPolicy.StrikeFacts(false, false, true, false, true)));
	}
}
