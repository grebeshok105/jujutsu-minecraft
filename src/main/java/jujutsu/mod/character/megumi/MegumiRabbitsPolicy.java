package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/** Pure Rabbit Escape numbers: upkeep gating, lifetime, bump readiness, and the bump shove. */
public final class MegumiRabbitsPolicy {
	private MegumiRabbitsPolicy() {}

	/**
	 * The upkeep tops the swarm up only while below strength and only once the respawn window has
	 * passed since the last top-up (initially the summon tick).
	 */
	public static boolean shouldRespawn(int alive, int size, long gameTime, long lastSpawnGameTime,
			int intervalTicks) {
		return alive < size && gameTime - lastSpawnGameTime >= intervalTicks;
	}

	/** The whole pack disperses once it has been out for the full lifetime. */
	public static boolean expired(long summonedAtGameTime, long gameTime, int lifetimeTicks) {
		return gameTime - summonedAtGameTime >= lifetimeTicks;
	}

	/** Each body bumps at most once per period; the body stores its own next window. */
	public static boolean bumpReady(long nextBumpGameTime, long gameTime) {
		return gameTime >= nextBumpGameTime;
	}

	/** Replacements per upkeep tick: the batch row, capped by the actual shortfall, never negative. */
	public static int respawnBatch(int alive, int size, int batch) {
		return Math.max(0, Math.min(batch, size - alive));
	}

	/**
	 * Additive shove away from the rabbit: the knockback row on the horizontal, half of it upward so
	 * the victim stumbles instead of sliding. A degenerate (stacked) pair shoves straight up.
	 */
	public static Vec3 bumpImpulse(Vec3 rabbitPos, Vec3 targetPos) {
		double dx = targetPos.x - rabbitPos.x;
		double dz = targetPos.z - rabbitPos.z;
		double horizontalSqr = dx * dx + dz * dz;
		double up = MegumiShikigamiProfile.RABBITS_BUMP_KNOCKBACK * 0.5;
		if (horizontalSqr < 1.0E-8) {
			return new Vec3(0.0, up, 0.0);
		}
		double horizontal = Math.sqrt(horizontalSqr);
		double push = MegumiShikigamiProfile.RABBITS_BUMP_KNOCKBACK / horizontal;
		return new Vec3(dx * push, up, dz * push);
	}
}
