package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/** Pure Toad numbers: tongue reach, the strike tick, and the grab velocity. */
public final class MegumiToadPolicy {
	private MegumiToadPolicy() {}

	/**
	 * Velocity dealt to the grabbed target on the strike tick: horizontal, pointing at the toad,
	 * at exactly {@link MegumiShikigamiProfile#TOAD_TONGUE_PULL_SPEED}, plus a small upward pop
	 * so a grounded target visibly leaves the floor. A target already on top of the toad gets no
	 * horizontal component (only the pop) instead of a NaN direction.
	 */
	public static Vec3 pullVelocity(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return new Vec3(0.0, MegumiShikigamiProfile.TOAD_TONGUE_PULL_UP, 0.0);
		}
		double length = Math.sqrt(lengthSqr);
		double speed = MegumiShikigamiProfile.TOAD_TONGUE_PULL_SPEED;
		return new Vec3(dx / length * speed, MegumiShikigamiProfile.TOAD_TONGUE_PULL_UP, dz / length * speed);
	}

	/** The tongue only grabs inside its range (inclusive boundary). */
	public static boolean canTongue(double distance) {
		return distance <= MegumiShikigamiProfile.TOAD_TONGUE_RANGE;
	}

	/**
	 * The strike lands on the last windup tick: {@code beginAction} counts the timer down in the
	 * shared base tick before the brain runs, so {@code 1} is the final live tick of the action.
	 */
	public static boolean strikeTickReached(int actionTicks) {
		return actionTicks == 1;
	}
}
