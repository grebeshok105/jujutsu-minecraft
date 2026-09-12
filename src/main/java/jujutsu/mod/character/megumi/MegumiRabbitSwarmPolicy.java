package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/**
 * Pure locomotion maths for Rabbit Escape (issue #78). The swarm ships no wander goal, its
 * {@code FollowOwnerGoal} starts only beyond six blocks while the bodies spawn on a 2.2-block ring,
 * and the hop {@code MoveControl} returns immediately without a wanted position — so the bodies
 * stood in the summon ring animating a run. These helpers give a body a point worth hopping to,
 * without a level or an entity in sight.
 */
final class MegumiRabbitSwarmPolicy {
	private MegumiRabbitSwarmPolicy() {
	}

	/** The drift point on the ring around the owner: angle in radians, radius inside the profile bounds. */
	static Vec3 driftTarget(double ownerX, double ownerY, double ownerZ, double angleRadians, double radius) {
		return new Vec3(
				ownerX + Math.cos(angleRadians) * radius,
				ownerY,
				ownerZ + Math.sin(angleRadians) * radius);
	}

	/** Radius for a drift step: {@code roll} in {@code [0, 1)} maps into the profile band. */
	static double driftRadius(double roll) {
		double clamped = Math.min(Math.max(roll, 0.0), 1.0);
		return MegumiShikigamiProfile.RABBIT_DRIFT_MIN_RADIUS
				+ clamped * (MegumiShikigamiProfile.RABBIT_DRIFT_MAX_RADIUS
						- MegumiShikigamiProfile.RABBIT_DRIFT_MIN_RADIUS);
	}

	/** Inside the leash a body drifts around the owner; outside it the follow goal takes over. */
	static boolean shouldDrift(double distanceToOwner) {
		return distanceToOwner <= MegumiShikigamiProfile.RABBIT_DRIFT_LEASH;
	}

	/** A body keeps closing on its mark until it is inside bump range; the brain lands the shove. */
	static boolean shouldChase(double distanceToTarget) {
		return distanceToTarget > MegumiShikigamiProfile.RABBITS_BUMP_RADIUS;
	}
}
