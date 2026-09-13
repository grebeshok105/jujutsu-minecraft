package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/**
 * Pure Toad numbers: who may be grabbed, how long a hold lasts, where the victim hangs and where it
 * flies. The tongue is only the visual — the mechanic is the grab (issue #79: the toad became a
 * grappler, so the old damage-and-pull numbers are gone).
 */
public final class MegumiToadPolicy {
	private MegumiToadPolicy() {}

	/** Grabbing is on and the victim is inside reach (inclusive boundary). */
	public static boolean canGrab(double distance) {
		return MegumiShikigamiProfile.TOAD_GRAB_ENABLED
				&& distance <= MegumiShikigamiProfile.TOAD_GRAB_RANGE;
	}

	/**
	 * How long the grab holds, in ticks: heavier and bulkier victims are held shorter, the body's
	 * hitbox volume only counts for non-players (a player should not lose hold time for being
	 * player-sized) and the result is clamped into the hard band so no input can produce a hold
	 * that is instantly over or endless.
	 */
	public static int holdTicksFor(double maxHealth, double volume, boolean isPlayer) {
		double ticks = MegumiShikigamiProfile.TOAD_GRAB_HOLD_BASE
				- maxHealth * MegumiShikigamiProfile.TOAD_GRAB_HOLD_HP_PENALTY;
		if (!isPlayer) {
			ticks -= volume * MegumiShikigamiProfile.TOAD_GRAB_HOLD_SIZE_PENALTY;
		}
		return clamp((int) Math.round(ticks),
				MegumiShikigamiProfile.TOAD_GRAB_HOLD_MIN, MegumiShikigamiProfile.TOAD_GRAB_HOLD_MAX);
	}

	/**
	 * Where the victim is pinned: {@code offset} blocks in front of the body along its horizontal
	 * look, at the body's own feet level. A zero-length look (impossible in play, reachable in a
	 * test) falls back to the body's position instead of a NaN direction.
	 */
	public static Vec3 anchor(Vec3 bodyPos, Vec3 bodyLook, double offset) {
		double dx = bodyLook.x;
		double dz = bodyLook.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return bodyPos;
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(bodyPos.x + dx / length * offset, bodyPos.y, bodyPos.z + dz / length * offset);
	}

	/**
	 * Throw direction: away from the owner, along the owner→toad line, at exactly {@code speed},
	 * plus {@code lift} upward. With the body on top of the owner the horizontal part is dropped
	 * (only the lift remains) rather than becoming a NaN.
	 */
	public static Vec3 throwVelocity(Vec3 ownerPos, Vec3 bodyPos, double speed, double lift) {
		double dx = bodyPos.x - ownerPos.x;
		double dz = bodyPos.z - ownerPos.z;
		double lengthSqr = dx * dx + dz * dz;
		if (lengthSqr < 1.0E-8) {
			return new Vec3(0.0, lift, 0.0);
		}
		double length = Math.sqrt(lengthSqr);
		return new Vec3(dx / length * speed, lift, dz / length * speed);
	}

	/** The hold snaps when the body has been dragged past its leash from the owner. */
	public static boolean bindBroken(double distance, double range) {
		return distance > range;
	}

	/**
	 * The tongue commits on the last windup tick: {@code beginAction} counts the timer down in the
	 * shared base tick before the brain runs, so {@code 1} is the final live tick of the action.
	 */
	public static boolean strikeTickReached(int actionTicks) {
		return actionTicks == 1;
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
