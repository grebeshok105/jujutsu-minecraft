package jujutsu.mod.character.megumi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Pure decisions of the Shift+B shadow move: which mode a tap resolves to, where "behind" a body is,
 * in what order the rear arc is tried, what cancels a backstep while hidden, and what each finished
 * mode costs. The runtime feeds facts in and places bodies out; every rule here runs in plain JUnit.
 */
class MegumiShadowMovePolicyTest {
	private static final double EPS = 1.0E-4;

	@Test
	void anEligibleTargetAlwaysWinsTheTapMode() {
		assertEquals(MegumiShadowMovePolicy.TapMode.BACKSTEP, MegumiShadowMovePolicy.resolveTapMode(true, false));
		assertEquals(MegumiShadowMovePolicy.TapMode.BACKSTEP, MegumiShadowMovePolicy.resolveTapMode(true, true));
	}

	@Test
	void surfaceStepRequiresAnAimedPointAndNoTarget() {
		assertEquals(MegumiShadowMovePolicy.TapMode.FREE_STEP, MegumiShadowMovePolicy.resolveTapMode(false, true));
		assertEquals(MegumiShadowMovePolicy.TapMode.REFUSED, MegumiShadowMovePolicy.resolveTapMode(false, false));
	}

	@Test
	void rearArcIsTriedStraightBehindFirstThenAlternatingNarrowToWide() {
		// The order is part of the contract: the first safe candidate wins, so square behind must
		// always beat a flank.
		assertArrayEquals(new float[] {0.0f, -25.0f, 25.0f, -50.0f, 50.0f, -75.0f, 75.0f},
				MegumiShadowMovePolicy.REAR_ARC_DEGREES);
	}

	@Test
	void behindPointLandsOnTheBackOfTheBody() {
		// Body yaw 0 faces +Z (south); the back is -Z.
		Vec3 south = MegumiShadowMovePolicy.behindPoint(
				new Vec3(0.0, 0.0, 0.0), 0.0f, 0.0f, MegumiProfile.BACKSTEP_DISTANCE);
		assertEquals(0.0, south.x, EPS);
		assertEquals(0.0, south.y, EPS);
		assertEquals(-MegumiProfile.BACKSTEP_DISTANCE, south.z, EPS);

		// Body yaw 90 faces -X (west); the back is +X.
		Vec3 west = MegumiShadowMovePolicy.behindPoint(
				new Vec3(0.0, 0.0, 0.0), 90.0f, 0.0f, MegumiProfile.BACKSTEP_DISTANCE);
		assertEquals(MegumiProfile.BACKSTEP_DISTANCE, west.x, EPS);
		assertEquals(0.0, west.y, EPS);
		assertEquals(0.0, west.z, EPS);
	}

	@Test
	void arcOffsetsSwingAroundTheTargetAtConstantDistance() {
		Vec3 target = new Vec3(10.0, 64.0, -5.0);
		Vec3 right = MegumiShadowMovePolicy.behindPoint(
				target, 0.0f, 90.0f, MegumiProfile.BACKSTEP_DISTANCE);
		Vec3 left = MegumiShadowMovePolicy.behindPoint(
				target, 0.0f, -90.0f, MegumiProfile.BACKSTEP_DISTANCE);
		assertEquals(target.x + MegumiProfile.BACKSTEP_DISTANCE, right.x, EPS);
		assertEquals(target.x - MegumiProfile.BACKSTEP_DISTANCE, left.x, EPS);
		assertEquals(target.z, right.z, EPS);
		assertEquals(target.z, left.z, EPS);

		for (float arc : MegumiShadowMovePolicy.REAR_ARC_DEGREES) {
			Vec3 point = MegumiShadowMovePolicy.behindPoint(
					target, 30.0f, arc, MegumiProfile.BACKSTEP_DISTANCE);
			assertEquals(MegumiProfile.BACKSTEP_DISTANCE, point.distanceTo(target), EPS,
					"arc " + arc + " must keep the exit at BACKSTEP_DISTANCE from the target");
		}
	}

	@Test
	void faceYawFacesThePointBeingLookedAt() {
		// Vanilla yaw: 0 = +Z, 90 = -X, -90 = +X; the comparison is angle-wrapped because the
		// policy may legally answer -180 where the caller expects 180.
		float south = MegumiShadowMovePolicy.faceYawDegrees(new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, 1.0));
		assertEquals(0.0f, Mth.wrapDegrees(south), EPS, "facing +Z is yaw 0");

		float west = MegumiShadowMovePolicy.faceYawDegrees(new Vec3(0.0, 0.0, 0.0), new Vec3(-1.0, 0.0, 0.0));
		assertEquals(90.0f, Mth.wrapDegrees(west), EPS, "facing -X is yaw 90");

		float east = MegumiShadowMovePolicy.faceYawDegrees(new Vec3(0.0, 0.0, 0.0), new Vec3(1.0, 0.0, 0.0));
		assertEquals(-90.0f, Mth.wrapDegrees(east), EPS, "facing +X is yaw -90");

		float north = MegumiShadowMovePolicy.faceYawDegrees(new Vec3(0.0, 0.0, 0.0), new Vec3(0.0, 0.0, -1.0));
		assertEquals(180.0f, Math.abs(Mth.wrapDegrees(north)), EPS, "facing -Z wraps to 180/-180");
	}

	@Test
	void aDeadRemovedOrForeignTargetCancelsTheBackstep() {
		assertFalse(MegumiShadowMovePolicy.backstepTargetStillHolds(false, false, true, 1.0));
		assertFalse(MegumiShadowMovePolicy.backstepTargetStillHolds(true, true, true, 1.0));
		assertFalse(MegumiShadowMovePolicy.backstepTargetStillHolds(true, false, false, 1.0));
	}

	@Test
	void aTargetDriftingPastTwiceTheCastRangeCancelsTheBackstep() {
		double limit = MegumiProfile.SHADOW_STEP_TARGET_RANGE * MegumiProfile.BACKSTEP_TARGET_DRIFT_MULTIPLIER;
		assertTrue(MegumiShadowMovePolicy.backstepTargetStillHolds(true, false, true, limit * limit * 0.99));
		assertTrue(MegumiShadowMovePolicy.backstepTargetStillHolds(true, false, true, limit * limit),
				"the drift boundary is inclusive");
		assertFalse(MegumiShadowMovePolicy.backstepTargetStillHolds(true, false, true, limit * limit + 1.0E-3));
	}

	@Test
	void deepSubmergeCostsMoreThanATapStep() {
		assertEquals(MegumiProfile.SHADOW_STEP_COOLDOWN_TICKS, MegumiShadowMovePolicy.cooldownTicks(false));
		assertEquals(MegumiProfile.SUBMERGE_COOLDOWN_TICKS, MegumiShadowMovePolicy.cooldownTicks(true));
		assertTrue(MegumiProfile.SUBMERGE_COOLDOWN_TICKS > MegumiProfile.SHADOW_STEP_COOLDOWN_TICKS,
				"one cooldown key, two prices: the deep submerge must cost more than a tap step");
	}
}
