package jujutsu.mod.character.megumi;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure state, aiming, sweep, and impact math for Piercing Ox. */
final class MegumiOxPolicy {
	private MegumiOxPolicy() {}

	static boolean canAcquire(AcquireFacts facts) {
		return facts.validMark()
				&& facts.corridorClear()
				&& facts.distance() <= MegumiShikigamiProfile.OX_ACQUIRE_RANGE;
	}

	static boolean aligned(Vec3 facing, Vec3 targetDirection, double maxAngleDegrees) {
		Vec3 facingHorizontal = horizontalUnit(facing);
		Vec3 targetHorizontal = horizontalUnit(targetDirection);
		if (facingHorizontal == Vec3.ZERO || targetHorizontal == Vec3.ZERO) {
			return false;
		}
		double angle = Math.max(0.0, Math.min(180.0, maxAngleDegrees));
		return facingHorizontal.dot(targetHorizontal) >= Math.cos(Math.toRadians(angle));
	}

	static Vec3 chargeDirection(Vec3 facing) {
		return horizontalUnit(facing);
	}

	static State nextState(State state, Event event) {
		return switch (state) {
			case FOLLOW -> event == Event.TARGET_AVAILABLE ? State.ACQUIRE : State.FOLLOW;
			case ACQUIRE -> switch (event) {
				case ACQUIRED -> State.ALIGN;
			case ABANDON -> State.RECOVERY;
			default -> State.ACQUIRE;
			};
			case ALIGN -> switch (event) {
				case ALIGNED -> State.WINDUP;
				case ABANDON -> State.RECOVERY;
				default -> State.ALIGN;
			};
			case WINDUP -> switch (event) {
				case WINDUP_COMPLETE -> State.CHARGE;
				case ABANDON -> State.RECOVERY;
				default -> State.WINDUP;
			};
			case CHARGE -> switch (event) {
				case ENTITY_HIT -> State.IMPACT;
				case CHARGE_FINISHED, WALL_ABORT -> State.RECOVERY;
				default -> State.CHARGE;
			};
			case IMPACT -> switch (event) {
				case PASS_THROUGH -> State.PASS_THROUGH;
				case CHARGE_FINISHED, WALL_ABORT -> State.RECOVERY;
				default -> State.IMPACT;
			};
			case PASS_THROUGH -> switch (event) {
				case ENTITY_HIT -> State.IMPACT;
				case CHARGE_FINISHED, WALL_ABORT -> State.RECOVERY;
				default -> State.PASS_THROUGH;
			};
			case RECOVERY -> event == Event.RECOVERY_COMPLETE ? State.FOLLOW : State.RECOVERY;
		};
	}

	/** Broad swept-body overlap followed by target-box clipping along the resolved body segment. */
	static boolean sweptHit(AABB prevBox, AABB curBox, AABB targetBox, Vec3 start, Vec3 end) {
		Vec3 resolvedDelta = end.subtract(start);
		double margin = MegumiShikigamiProfile.OX_SWEEP_MARGIN;
		AABB broadPhase = prevBox.expandTowards(resolvedDelta).inflate(margin);
		if (!broadPhase.intersects(targetBox)) {
			return false;
		}
		if (prevBox.inflate(margin).intersects(targetBox) || curBox.inflate(margin).intersects(targetBox)) {
			return true;
		}

		double halfX = prevBox.getXsize() * 0.5 + margin;
		double halfY = prevBox.getYsize() * 0.5 + margin;
		double halfZ = prevBox.getZsize() * 0.5 + margin;
		AABB clipBox = new AABB(targetBox.minX - halfX, targetBox.minY - halfY, targetBox.minZ - halfZ,
				targetBox.maxX + halfX, targetBox.maxY + halfY, targetBox.maxZ + halfZ);
		Vec3 centerOffset = new Vec3(
				(prevBox.minX + prevBox.maxX) * 0.5 - start.x,
				(prevBox.minY + prevBox.maxY) * 0.5 - start.y,
				(prevBox.minZ + prevBox.maxZ) * 0.5 - start.z);
		return clipBox.clip(start.add(centerOffset), end.add(centerOffset)).isPresent();
	}

	/** Measures only the displacement the collision-resolved move actually achieved. */
	static double resolvedTravel(Vec3 before, Vec3 after) {
		return before.distanceTo(after);
	}

	static double impactPower(double accumulatedDistance) {
		return Math.max(MegumiShikigamiProfile.OX_IMPACT_MIN,
				Math.min(MegumiShikigamiProfile.OX_IMPACT_MAX,
						MegumiShikigamiProfile.OX_IMPACT_BASE
								+ accumulatedDistance * MegumiShikigamiProfile.OX_IMPACT_SLOPE));
	}

	static boolean shouldAbort(boolean horizontalCollision, boolean verticalCollision,
			boolean onGround, int elapsedTicks) {
		return horizontalCollision
				|| verticalCollision && !onGround
				|| elapsedTicks >= MegumiShikigamiProfile.OX_CHARGE_MAX_TICKS;
	}

	/** Pure hit-set gate; the brain records a UUID only after this accepts the swept contact. */
	static boolean canRegisterHit(UUID targetUuid, Set<UUID> hitUuids) {
		return targetUuid != null && !hitUuids.contains(targetUuid);
	}

	private static Vec3 horizontalUnit(Vec3 vector) {
		Vec3 horizontal = new Vec3(vector.x, 0.0, vector.z);
		return horizontal.lengthSqr() < 1.0E-8 ? Vec3.ZERO : horizontal.normalize();
	}

	record AcquireFacts(boolean validMark, boolean corridorClear, double distance) {}

	enum State {
		FOLLOW,
		ACQUIRE,
		ALIGN,
		WINDUP,
		CHARGE,
		IMPACT,
		PASS_THROUGH,
		RECOVERY
	}

	enum Event {
		TARGET_AVAILABLE,
		ACQUIRED,
		ALIGNED,
		WINDUP_COMPLETE,
		ENTITY_HIT,
		PASS_THROUGH,
		CHARGE_FINISHED,
		WALL_ABORT,
		ABANDON,
		RECOVERY_COMPLETE,
		TARGET_MOVED
	}
}
