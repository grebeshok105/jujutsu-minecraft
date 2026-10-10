package jujutsu.mod.character.megumi;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pure decisions for the Piercing Ox: commit gates, the frozen charge line, swept-collision
 * geometry, and the distance-scaled impact formula. Every tunable reads
 * {@link MegumiShikigamiProfile}'s OX_* rows — nothing here touches the world.
 */
final class MegumiOxPolicy {
	private MegumiOxPolicy() {}

	/** What the commit tick knows about the world before the windup is bought. */
	record CommitFacts(
			boolean hasMark,
			boolean targetEligible,
			boolean hasLineOfSight,
			double distanceToTarget,
			boolean chargeReady,
			double corridorReach,
			double projectedStopToOwner) {}

	/**
	 * Whether the ox may buy a windup this tick. A mark is mandatory — the committed charger is
	 * an ordered weapon and never self-starts. The mark alone satisfies the spec's
	 * "see the target or hold a valid mark" clause: an order stands even through a wall, because
	 * the corridor trace is the honest reach gate (it clamps at the first abort point, so a
	 * charge that cannot reach its mark, or whose wall-clamped stop would leave the owner's
	 * leash, is refused before it ever starts).
	 */
	static boolean canCommit(CommitFacts facts) {
		return facts.hasMark()
				&& facts.targetEligible()
				&& facts.distanceToTarget() <= MegumiShikigamiProfile.OX_ACQUIRE_RANGE
				&& facts.chargeReady()
				&& facts.corridorReach() >= facts.distanceToTarget()
				&& facts.projectedStopToOwner() <= MegumiShikigamiProfile.RETURN_RADIUS;
	}

	/**
	 * The charge line: the horizontal direction {@code from → to}, normalized. A target standing
	 * in the same column leaves nothing to aim with — the windup cannot lock a degenerate line.
	 */
	static Vec3 lockDirection(Vec3 from, Vec3 to) {
		Vec3 horizontal = to.subtract(from).multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return Vec3.ZERO;
		}
		return horizontal.normalize();
	}

	/** Whether the body's facing is close enough to the charge line to buy the windup. */
	static boolean alignedEnough(double yawErrorDegrees) {
		return Math.abs(yawErrorDegrees) <= MegumiShikigamiProfile.OX_ALIGN_YAW_TOLERANCE_DEG;
	}

	/** What the charge does after a tick of real travel. */
	enum ChargeAction {
		CONTINUE,
		WALL_ABORT,
		PASS_THROUGH
	}

	/** The post-move facts of one charge tick. */
	record ChargeFacts(
			boolean horizontalCollision,
			boolean verticalCollision,
			boolean onGround,
			int stateTicks,
			double accumulatedDistance) {}

	/**
	 * World collision or a ledge drop ends the charge against the world (the wall impact); a
	 * charge that simply ran out of room or time ends in a pass-through. Order matters: a body
	 * that slams a wall on its last tick reads the abort, not the expiry. The vertical flag is
	 * reported but never gates the abort — the forced move's small gravity pull makes it true on
	 * every grounded tick; the wall signal is horizontal, the ledge signal is {@code !onGround}.
	 */
	static ChargeAction chargeAction(ChargeFacts facts) {
		if (facts.horizontalCollision() || !facts.onGround()) {
			return ChargeAction.WALL_ABORT;
		}
		if (facts.stateTicks() >= MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS
				|| facts.accumulatedDistance() >= MegumiShikigamiProfile.OX_CHARGE_MAX_DISTANCE) {
			return ChargeAction.PASS_THROUGH;
		}
		return ChargeAction.CONTINUE;
	}

	/**
	 * Real horizontal travel of the body between two sampled positions — never the requested
	 * velocity, so a charge stopped dead accumulates nothing.
	 */
	static double accumulatedDelta(Vec3 previousPosition, Vec3 currentPosition) {
		Vec3 delta = currentPosition.subtract(previousPosition);
		return Math.hypot(delta.x, delta.z);
	}

	/**
	 * The distance-scaled impact value, clamped to the profile band:
	 * {@code BASE + accumulatedDistance * SLOPE} inside {@code [MIN, MAX]}.
	 */
	static double impactPower(double accumulatedDistance) {
		return Math.min(MegumiShikigamiProfile.OX_IMPACT_MAX,
				Math.max(MegumiShikigamiProfile.OX_IMPACT_MIN,
						MegumiShikigamiProfile.OX_IMPACT_BASE
								+ accumulatedDistance * MegumiShikigamiProfile.OX_IMPACT_SLOPE));
	}

	/** The hit's damage roll — the impact power is the damage. */
	static double damageFor(double accumulatedDistance) {
		return impactPower(accumulatedDistance);
	}

	/** Knockback strength for a hit at this power: a base shove plus a per-power rider. */
	static double knockbackFor(double impactPower) {
		return MegumiShikigamiProfile.OX_KNOCKBACK_BASE
				+ impactPower * MegumiShikigamiProfile.OX_KNOCKBACK_PER_POWER;
	}

	/**
	 * Whether the moving body crossed {@code targetBox} this tick: overlap at rest, or the
	 * travelled segment clipped the target's inflated box — mirrors
	 * {@code MegumiPouncePolicy.sweptTargetHit} minus the steering.
	 */
	static boolean sweptHit(AABB oxBox, AABB targetBox, Vec3 previousPosition, Vec3 currentPosition) {
		return oxBox.inflate(0.30).intersects(targetBox)
				|| targetBox.inflate(0.30).clip(previousPosition, currentPosition).isPresent();
	}

	/** One hit per entity per charge — the set is cleared at every windup. */
	static boolean alreadyHit(Set<UUID> hitUuids, UUID candidateId) {
		return hitUuids.contains(candidateId);
	}

	/** Where a charge launched at {@code start} along {@code direction} stops after {@code reach} blocks. */
	static Vec3 projectedStop(Vec3 start, Vec3 direction, double reach) {
		return start.add(direction.scale(reach));
	}
}
