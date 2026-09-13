package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/**
 * Max Elephant's presence in pure numbers (issue #79): what the walking body does to everything
 * around it, and when its feet sweep the ground.
 *
 * <p>Two rules with different exceptions, which is exactly why they are not one method: everything
 * that is not on the owner's own side gets pushed, only what the hostility policy calls hostile
 * takes damage, and the body never touches itself.
 */
public final class MegumiElephantPresencePolicy {
	private MegumiElephantPresencePolicy() {}

	/** Whether a body at this distance is inside the presence at all (inclusive boundary). */
	public static boolean inside(double distance) {
		return distance <= MegumiShikigamiProfile.ELEPHANT_PRESENCE_RADIUS;
	}

	/** The presence ticks on a fixed period, not every tick. */
	public static boolean presenceDue(long gameTime) {
		return gameTime % MegumiShikigamiProfile.ELEPHANT_PRESENCE_PERIOD_TICKS == 0L;
	}

	/** The footprint sweeps on its own period, and only while the body is actually walking. */
	public static boolean footprintDue(long gameTime) {
		return gameTime % MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_PERIOD_TICKS == 0L;
	}

	/**
	 * Whether the body is moving enough for a footprint sweep. A standing elephant must not chew
	 * through the floor under itself.
	 */
	public static boolean footprintMoves(Vec3 deltaMovement) {
		return deltaMovement.x * deltaMovement.x + deltaMovement.z * deltaMovement.z
				> MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_MIN_SPEED
						* MegumiShikigamiProfile.ELEPHANT_FOOTPRINT_MIN_SPEED;
	}

	/** Which way the footprint reaches: the horizontal heading of the walk, or zero when idle. */
	public static Vec3 footprintHeading(Vec3 deltaMovement) {
		double dx = deltaMovement.x;
		double dz = deltaMovement.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return Vec3.ZERO;
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(dx / length, 0.0, dz / length);
	}

	/**
	 * The shove applied by the presence: horizontally away from the body at exactly {@code speed},
	 * straight up ({@code lift}) when the candidate stands on top of it, never NaN. A velocity
	 * impulse rather than {@code knockback()} on purpose — knockback resistance would eat it.
	 */
	public static Vec3 pushVelocity(Vec3 bodyPos, Vec3 candidatePos, double speed, double lift) {
		double dx = candidatePos.x - bodyPos.x;
		double dz = candidatePos.z - bodyPos.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return new Vec3(0.0, lift, 0.0);
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(dx / length * speed, lift, dz / length * speed);
	}
}
