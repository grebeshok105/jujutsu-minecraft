package jujutsu.mod.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * One server-side implementation of "this body is being held": the Toad's grab and a cursed
 * spirit's runner pin their victim the same way, so they share the code and the
 * {@link JujutsuEffects#GRIPPED} marker the client reads.
 *
 * <p>Collision handling is deliberately a policy, not a hidden runner special case. Runner pins
 * get three short steps back toward their holder when a hand anchor is inside solid geometry;
 * Toad pins keep their historic one-step fallback to the body's feet. Neither policy enables
 * noclip: the victim remains a normal collidable entity.
 */
public final class HoldSupport {
	/** Caller-specific wall-clamp behavior. */
	public enum CollisionPolicy {
		RUNNER(3),
		TOAD(1);

		private final int correctionTries;

		CollisionPolicy(int correctionTries) {
			this.correctionTries = correctionTries;
		}
	}

	private HoldSupport() {
	}
	/** Convenience form for callers using the shared ten-tick marker window. */
	public static void applyHold(LivingEntity holder, LivingEntity victim, Vec3 anchor,
			CollisionPolicy policy) {
		applyHold(holder, victim, anchor, policy, 10);
	}

	/**
	 * Pins {@code victim} to a collision-safe {@code anchor} for the next {@code markerTicks} and
	 * refreshes the marker. Safe to call every tick: the marker is a short re-applied buff, not a
	 * timer.
	 */
	public static void applyHold(LivingEntity holder, LivingEntity victim, Vec3 anchor,
			CollisionPolicy policy, int markerTicks) {
		if (holder == null || victim == null || anchor == null
				|| !HeldVictimRegistry.hold(holder, victim)) {
			return;
		}
		Vec3 safeAnchor = collisionSafeAnchor(holder, victim, anchor,
				policy == null ? CollisionPolicy.RUNNER : policy);
		victim.setDeltaMovement(Vec3.ZERO);
		victim.teleportTo(safeAnchor.x, safeAnchor.y, safeAnchor.z);
		// The teleport packet moves the position; hurtMarked carries the motion reset.
		victim.hurtMarked = true;
		victim.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED,
				Math.max(1, markerTicks), 0, false, false, false));
	}

	/** Drops the marker and exact holder/victim pair; idempotent on all release paths. */
	public static void release(LivingEntity victim) {
		HeldVictimRegistry.release(victim);
		victim.removeEffect(JujutsuEffects.GRIPPED);
	}

	/** Whether the body is currently marked as held — the client-side suppression reads the same. */
	public static boolean isHeld(LivingEntity victim) {
		return victim.hasEffect(JujutsuEffects.GRIPPED) || HeldVictimRegistry.isHeld(victim);
	}

	/**
	 * Resolves a hand anchor without putting the victim's box into a solid block. The initial anchor
	 * is the first candidate; failed candidates move monotonically toward the holder and never make
	 * more than the policy's bounded number of probes.
	 */
	static Vec3 collisionSafeAnchor(LivingEntity holder, LivingEntity victim, Vec3 anchor,
			CollisionPolicy policy) {
		if (isFree(victim, anchor)) {
			return anchor;
		}
		int tries = Math.max(1, policy.correctionTries);
		Vec3 lastFree = isFree(victim, victim.position()) ? victim.position() : null;
		for (int attempt = 1; attempt <= tries; attempt++) {
			Vec3 candidate = anchor.lerp(holder.position(), attempt / (double) tries);
			if (isFree(victim, candidate)) {
				lastFree = candidate;
				break;
			}
		}
		// The current body position is the last known free point when every probe is blocked. Keeping
		// it is safer than teleporting through a wall or enabling noPhysics as a broad workaround.
		return lastFree == null ? victim.position() : lastFree;
	}

	private static boolean isFree(LivingEntity victim, Vec3 position) {
		if (!(victim.level() instanceof ServerLevel level)) {
			return true;
		}
		AABB moved = victim.getBoundingBox().move(position.subtract(victim.position()));
		return level.noCollision(victim, moved);
	}
}
