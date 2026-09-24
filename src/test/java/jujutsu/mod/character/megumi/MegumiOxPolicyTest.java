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

final class MegumiOxPolicyTest {
	private static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);

	@Test
	void acquireRequiresAnEligibleMarkClearCorridorAndConfiguredRange() {
		double range = MegumiShikigamiProfile.OX_ACQUIRE_RANGE;
		assertTrue(MegumiOxPolicy.canAcquire(new MegumiOxPolicy.AcquireFacts(true, true, range)));
		assertFalse(MegumiOxPolicy.canAcquire(new MegumiOxPolicy.AcquireFacts(false, true, 1.0)));
		assertFalse(MegumiOxPolicy.canAcquire(new MegumiOxPolicy.AcquireFacts(true, false, 1.0)));
		assertFalse(MegumiOxPolicy.canAcquire(new MegumiOxPolicy.AcquireFacts(true, true, range + 0.01)));
	}

	@Test
	void chargeDistanceAccumulatesOnlyCollisionResolvedTravel() {
		Vec3 before = Vec3.ZERO;
		Vec3 requested = new Vec3(0.0, 0.0, 4.0);
		Vec3 afterFirstMove = new Vec3(0.0, 0.0, 1.25);
		Vec3 afterSecondMove = new Vec3(0.0, 0.0, 2.0);

		double accumulated = MegumiOxPolicy.resolvedTravel(before, afterFirstMove);
		accumulated += MegumiOxPolicy.resolvedTravel(afterFirstMove, afterSecondMove);
		assertEquals(2.0, accumulated, 1.0E-9);
		assertTrue(accumulated < requested.length());
	}

	@Test
	void alignmentAndCommitDirectionUseOnlyNormalizedHorizontalVectors() {
		Vec3 facing = new Vec3(3.0, 4.0, 0.0);
		Vec3 frozen = MegumiOxPolicy.chargeDirection(facing);
		assertEquals(EAST, frozen);
		assertTrue(MegumiOxPolicy.aligned(facing, new Vec3(1.0, 20.0, 0.0), 0.0));
		assertFalse(MegumiOxPolicy.aligned(facing, new Vec3(0.0, 0.0, 1.0), 12.0));
		assertEquals(Vec3.ZERO, MegumiOxPolicy.chargeDirection(new Vec3(0.0, 1.0, 0.0)));
	}

	@Test
	void committedStateIgnoresTargetMovementUntilTheChargeEnds() {
		Vec3 frozen = MegumiOxPolicy.chargeDirection(EAST);
		assertEquals(MegumiOxPolicy.State.CHARGE,
				MegumiOxPolicy.nextState(MegumiOxPolicy.State.CHARGE, MegumiOxPolicy.Event.TARGET_MOVED));
		assertEquals(MegumiOxPolicy.State.PASS_THROUGH,
				MegumiOxPolicy.nextState(MegumiOxPolicy.State.PASS_THROUGH, MegumiOxPolicy.Event.TARGET_MOVED));
		assertEquals(EAST, frozen, "later target movement cannot mutate the committed direction");
	}

	@Test
	void sweptBroadAndNarrowPhasesDetectCrossingsButRejectTheSidestep() {
		AABB previous = new AABB(-0.5, 0.0, -0.5, 0.5, 1.5, 0.5);
		AABB current = previous.move(0.0, 0.0, 4.0);
		AABB crossed = new AABB(-0.3, 0.0, 1.7, 0.3, 1.8, 2.3);
		AABB sidestepped = crossed.move(3.0, 0.0, 0.0);
		assertTrue(MegumiOxPolicy.sweptHit(previous, current, crossed,
				new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 4.0)));
		assertFalse(MegumiOxPolicy.sweptHit(previous, current, sidestepped,
				new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 4.0)));
		AABB withinBodyReach = new AABB(0.6, 0.0, 1.7, 0.9, 1.8, 2.3);
		assertTrue(MegumiOxPolicy.sweptHit(previous, current, withinBodyReach,
				new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 4.0)));
	}

	@Test
	void impactPowerStrictlyScalesWithTravelAndHonorsBothCaps() {
		double shortTravel = MegumiOxPolicy.impactPower(2.0);
		double longTravel = MegumiOxPolicy.impactPower(10.0);
		assertTrue(longTravel > shortTravel,
				"red-proof: setting OX_IMPACT_SLOPE to zero must fail this strict comparison");
		assertEquals(MegumiShikigamiProfile.OX_IMPACT_MIN, MegumiOxPolicy.impactPower(-100.0), 1.0E-9);
		assertEquals(MegumiShikigamiProfile.OX_IMPACT_MAX,
				MegumiOxPolicy.impactPower(100_000.0), 1.0E-9);
	}

	@Test
	void wallAndCeilingAbortButGroundContactDoesNot() {
		assertTrue(MegumiOxPolicy.shouldAbort(true, false, true, 1));
		assertTrue(MegumiOxPolicy.shouldAbort(false, true, false, 1));
		assertFalse(MegumiOxPolicy.shouldAbort(false, true, true, 1));
		assertTrue(MegumiOxPolicy.shouldAbort(false, false, true,
				MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS));
	}

	@Test
	void hitSetAllowsEachSweptEntityOnlyOncePerCharge() {
		UUID first = UUID.randomUUID();
		UUID second = UUID.randomUUID();
		Set<UUID> hit = new HashSet<>();
		AABB previous = new AABB(-0.5, 0.0, -0.5, 0.5, 1.5, 0.5);
		AABB current = previous.move(0.0, 0.0, 2.0);
		AABB target = new AABB(-0.3, 0.0, 0.8, 0.3, 1.8, 1.2);
		Vec3 start = new Vec3(0.0, 0.0, 0.0);
		Vec3 end = new Vec3(0.0, 0.0, 2.0);

		assertTrue(MegumiOxPolicy.sweptHit(previous, current, target, start, end));
		assertTrue(MegumiOxPolicy.canRegisterHit(first, hit));
		hit.add(first);
		assertFalse(MegumiOxPolicy.canRegisterHit(first, hit));
		assertTrue(MegumiOxPolicy.sweptHit(previous, current, target, start, end));
		assertTrue(MegumiOxPolicy.canRegisterHit(second, hit));
		assertFalse(MegumiOxPolicy.canRegisterHit(null, hit));
	}

	@Test
	void stateMachineTraversesTheCommittedChargeAndRecovery() {
		MegumiOxPolicy.State state = MegumiOxPolicy.State.FOLLOW;
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.TARGET_AVAILABLE);
		assertEquals(MegumiOxPolicy.State.ACQUIRE, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.ACQUIRED);
		assertEquals(MegumiOxPolicy.State.ALIGN, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.ALIGNED);
		assertEquals(MegumiOxPolicy.State.WINDUP, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.WINDUP_COMPLETE);
		assertEquals(MegumiOxPolicy.State.CHARGE, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.ENTITY_HIT);
		assertEquals(MegumiOxPolicy.State.IMPACT, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.PASS_THROUGH);
		assertEquals(MegumiOxPolicy.State.PASS_THROUGH, state);
		state = MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.CHARGE_FINISHED);
		assertEquals(MegumiOxPolicy.State.RECOVERY, state);
		assertEquals(MegumiOxPolicy.State.FOLLOW,
				MegumiOxPolicy.nextState(state, MegumiOxPolicy.Event.RECOVERY_COMPLETE));
	}
}
