package jujutsu.mod.character.megumi;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Pure decisions of the Shift+B shadow move: which mode a tap resolves to, where "behind" a body is,
 * in what order the rear arc is tried, and what each finished mode costs. No world access — the
 * runtime feeds facts in and places bodies out, which is what keeps every rule here executable in
 * plain JUnit.
 */
final class MegumiShadowMovePolicy {
	/**
	 * Rear-arc yaw offsets in degrees, in the order they are tried: straight behind first, then
	 * alternating left/right from narrow to wide. The order is part of the contract — the first safe
	 * candidate wins, so a point square behind the target always beats a flank.
	 */
	static final float[] REAR_ARC_DEGREES = {0.0f, -25.0f, 25.0f, -50.0f, 50.0f, -75.0f, 75.0f};

	private MegumiShadowMovePolicy() {}

	enum TapMode {
		/** An eligible living target under the crosshair: emerge behind its back. */
		BACKSTEP,
		/** No target, but a surface point within range: free shadow step. */
		FREE_STEP,
		/** Nothing to travel to; the cast is refused before any state exists. */
		REFUSED
	}

	static TapMode resolveTapMode(boolean eligibleEntityAimed, boolean surfacePointAimed) {
		if (eligibleEntityAimed) {
			return TapMode.BACKSTEP;
		}
		return surfacePointAimed ? TapMode.FREE_STEP : TapMode.REFUSED;
	}

	/**
	 * A point {@code distance} blocks behind a body, swung {@code arcOffsetDegrees} around it.
	 *
	 * <p>"Behind" is the back of the <b>body</b> ({@code yBodyRot}), not the head: the head can be
	 * turned 75° away while the spine — and the blind spot — stays put. Nail embedding already made
	 * the same choice for body-relative geometry.
	 */
	static Vec3 behindPoint(Vec3 targetPosition, float targetBodyYawDegrees, float arcOffsetDegrees, double distance) {
		float yawRadians = (targetBodyYawDegrees + arcOffsetDegrees) * Mth.DEG_TO_RAD;
		// Body forward for yaw is (-sin, 0, cos); behind is its negation.
		double behindX = Mth.sin(yawRadians) * distance;
		double behindZ = -Mth.cos(yawRadians) * distance;
		return targetPosition.add(behindX, 0.0, behindZ);
	}

	/** The yaw that makes a body standing at {@code from} face {@code to}. */
	static float faceYawDegrees(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
	}

	/**
	 * Whether a backstep target that was valid at cast time is still worth emerging behind. Death,
	 * removal, level change and drifting past twice the cast range all cancel back to the start point.
	 */
	static boolean backstepTargetStillHolds(boolean alive, boolean removed, boolean sameLevel, double distanceSqr) {
		double limit = MegumiProfile.SHADOW_STEP_TARGET_RANGE * MegumiProfile.BACKSTEP_TARGET_DRIFT_MULTIPLIER;
		return alive && !removed && sameLevel && distanceSqr <= limit * limit;
	}

	/** One cooldown key, two prices: the deep submerge costs more than a tap step. */
	static int cooldownTicks(boolean submerge) {
		return submerge ? MegumiProfile.SUBMERGE_COOLDOWN_TICKS : MegumiProfile.SHADOW_STEP_COOLDOWN_TICKS;
	}
}
