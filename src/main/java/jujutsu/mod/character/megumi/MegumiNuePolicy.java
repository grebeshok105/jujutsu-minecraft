package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/** Pure Nue numbers: dive steering, impact, and the soaked-target escalation. */
public final class MegumiNuePolicy {
	private MegumiNuePolicy() {}

	/** Velocity for one dive tick: straight at the target's eyes, no gravity. */
	public static Vec3 diveVelocity(Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		if (delta.lengthSqr() < 1.0E-8) {
			return Vec3.ZERO;
		}
		return delta.normalize().scale(MegumiShikigamiProfile.NUE_DIVE_SPEED);
	}

	/**
	 * The dive lands when the target's hitbox comes within the impact radius of the body. Measured as a
	 * box-to-point distance (not feet-to-feet): the dive steers at the target's eyes, so a feet-based check
	 * would let the flyer hover one body-height above the target forever without ever landing a hit.
	 */
	public static boolean impactReachedSq(double distanceSqr) {
		return distanceSqr <= MegumiShikigamiProfile.NUE_IMPACT_RADIUS * MegumiShikigamiProfile.NUE_IMPACT_RADIUS;
	}

	public static boolean canStartDive(double distance, boolean hasLineOfSight, boolean ready) {
		return ready && hasLineOfSight && distance <= MegumiShikigamiProfile.NUE_DIVE_TRIGGER_RANGE;
	}

	/** Wet targets take the canon combo: harder hit, longer stun, longer slow. */
	public static double impactDamage(boolean soaked) {
		return MegumiShikigamiProfile.NUE_DIVE_DAMAGE
				* (soaked ? MegumiShikigamiProfile.NUE_SOAKED_DAMAGE_MULTIPLIER : 1.0);
	}

	public static int stunTicks(boolean soaked) {
		return soaked ? MegumiShikigamiProfile.NUE_STUN_TICKS_SOAKED : MegumiShikigamiProfile.NUE_STUN_TICKS;
	}

	public static int slowTicks(boolean soaked) {
		return soaked ? MegumiShikigamiProfile.NUE_SLOW_TICKS_SOAKED : MegumiShikigamiProfile.NUE_SLOW_TICKS;
	}
}