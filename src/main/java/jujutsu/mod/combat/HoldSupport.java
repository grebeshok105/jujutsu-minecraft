package jujutsu.mod.combat;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.cursedspirit.hold.HeldVictimRegistry;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * One server-side implementation of "this body is being held": the Toad's grab and a cursed
 * spirit's runner share the {@link JujutsuEffects#GRIPPED} marker and the
 * {@link HeldVictimRegistry} pair table, but they pin differently. The Toad keeps the classic
 * per-tick pin through {@link #applyHold}; the runner (issue #119) mounts its victim and only
 * refreshes state through {@link #refreshMountedHold} — the seat position comes from the
 * vehicle's {@code positionRider}, never from a per-tick teleport.
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
	/**
	 * Per-tick pull speed toward the hold anchor, in blocks. 0.5 keeps the grab-start drag
	 * readable (~10 ticks to cross a 5-block gap) while staying under the entity tracker's
	 * teleport threshold, so mob carries lerp client-side instead of snapping.
	 */
	private static final double MAX_PULL_STEP = 0.5;

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
		victim.hurtMarked = true;
		// Smooth carry: never snap the victim straight onto the anchor. A hard teleportTo
		// every tick reads as a teleport at grab start and as a jitter whenever the anchor
		// jumps (wall clamp, holder turn). Cap the per-tick pull instead — for mobs setPos
		// rides the entity tracker, so observers get a real 3-tick lerp; for players the
		// small relative teleports approximate the same pull (vanilla cannot lerp the
		// local player — isLocalInstanceAuthoritative ignores the sync packet).
		Vec3 target = pullStep(victim, safeAnchor);
		if (victim instanceof ServerPlayer) {
			victim.teleportTo(target.x, target.y, target.z);
		} else {
			victim.setPos(target.x, target.y, target.z);
		}
		victim.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED,
				Math.max(1, markerTicks), 0, false, false, false));
	}

	/**
	 * Mounted-carry state refresh (issue #119): reaffirms the holder/victim pair and the
	 * {@code GRIPPED} marker without touching the victim's position — a mounted victim's seat
	 * is owned by the vehicle's {@code positionRider}, so a positional write here would fight
	 * the attachment. Safe to call every tick.
	 *
	 * <p>Returns false when the registry refused the pair (another holder owns the victim):
	 * the caller must abort the carry instead of mounting a body it does not hold.
	 */
	public static boolean refreshMountedHold(LivingEntity holder, LivingEntity victim,
			int markerTicks) {
		if (holder == null || victim == null || !HeldVictimRegistry.hold(holder, victim)) {
			return false;
		}
		victim.setDeltaMovement(Vec3.ZERO);
		victim.hurtMarked = true;
		victim.addEffect(new MobEffectInstance(JujutsuEffects.GRIPPED,
				Math.max(1, markerTicks), 0, false, false, false));
		return true;
	}

	/**
	 * The collision-safe seat anchor for a mounted runner victim: the same wall clamp the old
	 * tick-teleport pin used, now applied to the passenger position so a hand anchor inside
	 * solid geometry never suffocates the carried body.
	 */
	public static Vec3 mountedAnchor(LivingEntity holder, LivingEntity victim, Vec3 anchor) {
		return collisionSafeAnchor(holder, victim, anchor, CollisionPolicy.RUNNER);
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

	/**
	 * One tick of the pull toward {@code anchor}, capped at {@link #MAX_PULL_STEP} blocks.
	 * A victim already inside solid geometry (a wall appeared around it mid-hold) snaps
	 * straight to the collision-safe anchor — the capped step cannot help when every
	 * intermediate box still clips. Otherwise a blocked step holds position for that tick
	 * rather than clipping the victim through a wall the anchor clamp already rejected.
	 */
	static Vec3 pullStep(LivingEntity victim, Vec3 anchor) {
		// Emergency extraction: the victim is already inside solid geometry (a wall appeared
		// around it mid-hold). The capped step cannot help — every intermediate box still
		// clips — so snap straight to the collision-safe anchor instead of leaving it to
		// suffocate. Normal holds never take this branch.
		if (!isFree(victim, victim.position())) {
			return anchor;
		}
		Vec3 delta = anchor.subtract(victim.position());
		double dist = delta.length();
		if (dist <= MAX_PULL_STEP || dist < 1.0E-6) {
			return anchor;
		}
		Vec3 target = victim.position().add(delta.scale(MAX_PULL_STEP / dist));
		return isFree(victim, target) ? target : victim.position();
	}

	private static boolean isFree(LivingEntity victim, Vec3 position) {
		// Level.noCollision exists on both sides: the client replica must clamp the same
		// anchor the server does, or a wall-adjacent carry diverges the two seats (issue
		// #119 — positionRider runs on the client too).
		AABB moved = victim.getBoundingBox().move(position.subtract(victim.position()));
		return victim.level().noCollision(victim, moved);
	}
}
