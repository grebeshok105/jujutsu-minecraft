package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Pure-decision pins for {@link MegumiOxPolicy}: the distance-scaled impact band, the commit
 * gates, the frozen lock line, and the swept-collision geometry.
 */
class MegumiOxPolicyTest {

	private static MegumiOxPolicy.CommitFacts happyCommit() {
		return new MegumiOxPolicy.CommitFacts(true, true, false, 10.0, true, 12.0, 8.0);
	}

	// --- impact scaling -------------------------------------------------------

	@Test
	void theImpactBandScalesWithRealDistance() {
		assertEquals(2.0, MegumiOxPolicy.impactPower(0.0), 1.0E-9, "a charge that never ran hits the floor");
		assertEquals(3.05, MegumiOxPolicy.impactPower(3.0), 1.0E-9, "the short-corridor band");
		assertEquals(4.45, MegumiOxPolicy.impactPower(7.0), 1.0E-9, "the arena-corridor band");
		assertEquals(9.0, MegumiOxPolicy.impactPower(20.0), 1.0E-9, "full-length charge");
		assertEquals(12.0, MegumiOxPolicy.impactPower(30.0), 1.0E-9, "the power never exceeds the cap");
	}

	@Test
	void damageIsTheImpactPower() {
		assertEquals(MegumiOxPolicy.impactPower(6.5), MegumiOxPolicy.damageFor(6.5), 1.0E-9);
	}

	@Test
	void knockbackRidesThePower() {
		assertEquals(1.0, MegumiOxPolicy.knockbackFor(2.0), 1.0E-9);
		assertEquals(2.0, MegumiOxPolicy.knockbackFor(12.0), 1.0E-9);
	}

	// --- commit gates ---------------------------------------------------------

	@Test
	void aMarkedInRangeTargetWithRoomCommits() {
		assertTrue(MegumiOxPolicy.canCommit(happyCommit()));
	}

	@Test
	void noMarkNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				false, true, false, 10.0, true, 12.0, 8.0)),
				"the committed charger is an ordered weapon, never a self-start");
	}

	@Test
	void anIneligibleTargetNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, false, true, 10.0, true, 12.0, 8.0)));
	}

	@Test
	void aTargetPastAcquireRangeNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, true, 15.01, true, 20.0, 5.0)));
		assertTrue(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, true, 15.0, true, 20.0, 5.0)),
				"the range edge itself still commits");
	}

	@Test
	void aChargedCooldownNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, false, 10.0, false, 12.0, 8.0)));
	}

	@Test
	void aCorridorTooShortForTheMarkNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, false, 10.0, true, 9.99, 8.0)),
				"a wall clamped before the mark makes the charge a dead run");
	}

	@Test
	void aStopBeyondTheLeashNeverCommits() {
		assertFalse(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, false, 10.0, true, 12.0, 20.01)),
				"the wall-clamped stop must stay inside the owner's leash");
		assertTrue(MegumiOxPolicy.canCommit(new MegumiOxPolicy.CommitFacts(
				true, true, false, 10.0, true, 12.0, 20.0)),
				"the leash edge itself still commits");
	}

	// --- the frozen line ------------------------------------------------------

	@Test
	void theLockLineIsHorizontalAndNormalized() {
		Vec3 dir = MegumiOxPolicy.lockDirection(new Vec3(1.0, 0.0, 1.0), new Vec3(4.0, 3.0, 5.0));
		assertEquals(0.0, dir.y, 1.0E-9, "the charge stays on the ground plane");
		assertEquals(1.0, dir.length(), 1.0E-9);
		assertEquals(new Vec3(3.0, 0.0, 4.0).normalize(), dir);
	}

	@Test
	void aColumnedTargetLocksNowhere() {
		assertEquals(Vec3.ZERO, MegumiOxPolicy.lockDirection(
				new Vec3(2.0, 0.0, 2.0), new Vec3(2.0, 5.0, 2.0)));
	}

	@Test
	void alignHoldsAnEightDegreeWindow() {
		assertTrue(MegumiOxPolicy.alignedEnough(8.0), "the tolerance edge is still aligned");
		assertTrue(MegumiOxPolicy.alignedEnough(-7.9));
		assertFalse(MegumiOxPolicy.alignedEnough(8.01));
	}

	// --- charge resolution ----------------------------------------------------

	@Test
	void theChargeRunsUntilTheWorldOrTheBudgetStopsIt() {
		assertEquals(MegumiOxPolicy.ChargeAction.CONTINUE, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(false, false, true, 10, 5.0)));
	}

	@Test
	void aWallAbortsTheCharge() {
		assertEquals(MegumiOxPolicy.ChargeAction.WALL_ABORT, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(true, false, true, 10, 5.0)));
	}

	@Test
	void steppingOffALedgeAbortsTheCharge() {
		assertEquals(MegumiOxPolicy.ChargeAction.WALL_ABORT, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(false, false, false, 10, 5.0)),
				"a grounded body that lost its floor ends committed");
	}

	@Test
	void plainGroundContactNeverAborts() {
		assertEquals(MegumiOxPolicy.ChargeAction.CONTINUE, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(false, true, true, 10, 5.0)),
				"the gravity pull inside the move flags vertical contact every grounded tick");
	}

	@Test
	void runningOutOfTicksEndsInPassThrough() {
		assertEquals(MegumiOxPolicy.ChargeAction.PASS_THROUGH, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(false, false, true, 60, 5.0)));
	}

	@Test
	void runningOutOfDistanceEndsInPassThrough() {
		assertEquals(MegumiOxPolicy.ChargeAction.PASS_THROUGH, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(false, false, true, 10, 20.0)));
	}

	@Test
	void theWallWinsOverTheExpiry() {
		assertEquals(MegumiOxPolicy.ChargeAction.WALL_ABORT, MegumiOxPolicy.chargeAction(
				new MegumiOxPolicy.ChargeFacts(true, false, true, 60, 20.0)),
				"a body that slams a wall on its last tick reads the abort, not the expiry");
	}

	// --- travelled distance + swept geometry ----------------------------------

	@Test
	void onlyHorizontalTravelAccumulates() {
		Vec3 before = new Vec3(0.0, 0.0, 0.0);
		Vec3 after = new Vec3(3.0, 2.0, 4.0);
		assertEquals(5.0, MegumiOxPolicy.accumulatedDelta(before, after), 1.0E-9,
				"the vertical leg never feeds the impact formula");
		assertEquals(0.0, MegumiOxPolicy.accumulatedDelta(before, before), 1.0E-9,
				"a body stopped dead accumulates nothing");
	}

	@Test
	void theSweptSegmentCatchesCrossedTargets() {
		AABB oxBox = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		AABB target = new AABB(2.0, 0.0, 0.0, 3.0, 1.0, 1.0);
		assertTrue(MegumiOxPolicy.sweptHit(oxBox, target, new Vec3(0.5, 0.0, 0.5), new Vec3(3.5, 0.0, 0.5)),
				"the travelled segment clipped the inflated target box");
	}

	@Test
	void restingOverlapAlsoHits() {
		AABB oxBox = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		AABB target = new AABB(0.8, 0.0, 0.0, 1.8, 1.0, 1.0);
		assertTrue(MegumiOxPolicy.sweptHit(oxBox, target, Vec3.ZERO, Vec3.ZERO));
	}

	@Test
	void aTargetTheLineMissedIsUntouched() {
		AABB oxBox = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
		AABB target = new AABB(2.0, 0.0, 5.0, 3.0, 1.0, 6.0);
		assertFalse(MegumiOxPolicy.sweptHit(oxBox, target,
				new Vec3(0.5, 0.0, 0.5), new Vec3(3.5, 0.0, 0.5)));
	}

	@Test
	void theHitSetDeduplicatesPerCharge() {
		Set<UUID> hits = new HashSet<>();
		UUID victim = UUID.randomUUID();
		assertFalse(MegumiOxPolicy.alreadyHit(hits, victim));
		hits.add(victim);
		assertTrue(MegumiOxPolicy.alreadyHit(hits, victim));
	}

	@Test
	void theProjectedStopLandsOnTheLine() {
		Vec3 stop = MegumiOxPolicy.projectedStop(new Vec3(0.0, 0.0, 0.0),
				new Vec3(1.0, 0.0, 0.0), 7.5);
		assertEquals(new Vec3(7.5, 0.0, 0.0), stop);
	}
}
