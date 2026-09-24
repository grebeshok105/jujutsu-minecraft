package jujutsu.mod.character.megumi;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/** Pure rules for the Great Serpent's ambush, binding, and lifecycle transitions. */
public final class MegumiSerpentPolicy {
	private static final double DIRECTION_EPSILON_SQR = 1.0E-8;
	private static final double DIAGONAL_SCALE = Math.sqrt(0.5);

	private MegumiSerpentPolicy() {}

	public enum State {
		FOLLOW_READY,
		PREPARE_AMBUSH,
		SUBMERGED,
		EMERGE,
		BIND,
		RELEASE,
		RECOVERY
	}

	/** A single current mark; mark precedence is resolved by the shared pack before this policy runs. */
	public enum MarkKind {
		NONE,
		MANUAL,
		RETALIATION,
		AUTONOMOUS
	}

	public enum Event {
		MARK_READY,
		PREPARE_COMPLETE,
		SUBMERGE_COMPLETE,
		EMERGE_BINDABLE,
		EMERGE_INVALID,
		TARGET_INVALID,
		NO_SAFE_EMERGE,
		BIND_TIMER,
		BIND_LEASH,
		MANUAL_RECALL,
		SERPENT_DEATH,
		SERPENT_REMOVED,
		SERPENT_TEARDOWN,
		OWNER_DEATH,
		OWNER_DISCONNECT,
		OWNER_RESPAWN,
		OWNER_DIMENSION_CHANGE,
		VICTIM_DEATH,
		VICTIM_DISCONNECT,
		VICTIM_DIMENSION_CHANGE,
		VICTIM_UNLOAD,
		VICTIM_INELIGIBLE,
		HOLD_OWNERSHIP_MISMATCH,
		SERVER_TEARDOWN,
		DESELECTED,
		FIXTURE_RESET,
		RELEASE_COMPLETE,
		RECOVERY_COMPLETE
	}

	/** Inputs to the first mark gate; a manual order also requires the owner's sightline. */
	public record AmbushFacts(MarkKind markKind, boolean targetEligible, boolean inRange,
			boolean serpentLineOfSight, boolean ownerLineOfSight) {}

	/** Commit-time gates; each fact is computed from the live target and world. */
	public record BindFacts(boolean alive, boolean removed, boolean ownerEligible,
			boolean passenger, boolean alreadyHeld, boolean ungrabbable,
			boolean inRange, boolean lineOfSight) {}

	/** Independent world-placement facts so collision, chunk, and border rules stay testable. */
	public record SafetyFacts(boolean finitePosition, boolean inWorldBounds, boolean chunkLoaded,
			boolean withinWorldBorder, boolean collisionFree) {}

	/** A mark may start only when the body and owner can still commit to the selected target. */
	public static boolean canStart(AmbushFacts facts) {
		return facts != null
				&& facts.markKind() != null
				&& facts.markKind() != MarkKind.NONE
				&& facts.targetEligible()
				&& facts.inRange()
				&& facts.serpentLineOfSight()
				&& (facts.markKind() != MarkKind.MANUAL || facts.ownerLineOfSight());
	}

	/** The final restraint gate; a target that became unsafe during the ambush is never bound. */
	public static boolean canBind(BindFacts facts) {
		return facts != null
				&& facts.alive()
				&& !facts.removed()
				&& facts.ownerEligible()
				&& !facts.passenger()
				&& !facts.alreadyHeld()
				&& !facts.ungrabbable()
				&& facts.inRange()
				&& facts.lineOfSight();
	}

	/** The bind holds at the leash boundary and breaks only beyond it. Invalid measurements fail safe. */
	public static boolean bindBroken(double serpentOwnerDistance, double maxDistance) {
		return !Double.isFinite(serpentOwnerDistance)
				|| !Double.isFinite(maxDistance)
				|| maxDistance < 0.0
				|| serpentOwnerDistance > maxDistance;
	}

	/**
	 * Candidate points on a horizontal ring around the target, beginning on the side from which the
	 * serpent approached. The approach vector points from the target toward the emerging body.
	 */
	public static List<Vec3> emergeCandidates(Vec3 targetPos, Vec3 approachDir, double radius) {
		if (!finite(targetPos) || approachDir == null || !Double.isFinite(radius) || radius < 0.0) {
			return List.of();
		}
		if (radius == 0.0) {
			return List.of(targetPos);
		}
		Vec3 forward = new Vec3(approachDir.x, 0.0, approachDir.z);
		if (!finite(forward)) {
			return List.of();
		}
		if (forward.lengthSqr() <= DIRECTION_EPSILON_SQR) {
			forward = new Vec3(0.0, 0.0, 1.0);
		} else {
			forward = forward.normalize();
		}
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		Vec3 diagonalForward = forward.add(right).scale(DIAGONAL_SCALE);
		Vec3 diagonalBack = forward.subtract(right).scale(DIAGONAL_SCALE);
		return List.of(
				targetPos.add(forward.scale(radius)),
				targetPos.add(diagonalForward.scale(radius)),
				targetPos.add(right.scale(radius)),
				targetPos.subtract(diagonalBack.scale(radius)),
				targetPos.subtract(forward.scale(radius)),
				targetPos.subtract(diagonalForward.scale(radius)),
				targetPos.subtract(right.scale(radius)),
				targetPos.add(diagonalBack.scale(radius)));
	}

	/** A teleport is allowed only when every independent placement condition remains true. */
	public static boolean isSafeEmerge(SafetyFacts facts) {
		return facts != null
				&& facts.finitePosition()
				&& facts.inWorldBounds()
				&& facts.chunkLoaded()
				&& facts.withinWorldBorder()
				&& facts.collisionFree();
	}

	/** Deterministic state transitions; all release events share the BIND → RELEASE seam. */
	public static State nextState(State state, Event event) {
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(event, "event");
		if (isReleaseEvent(event)) {
			return state == State.BIND ? State.RELEASE
					: state == State.FOLLOW_READY ? State.FOLLOW_READY : State.RECOVERY;
		}
		return switch (state) {
			case FOLLOW_READY -> event == Event.MARK_READY ? State.PREPARE_AMBUSH : State.FOLLOW_READY;
			case PREPARE_AMBUSH -> switch (event) {
				case PREPARE_COMPLETE -> State.SUBMERGED;
				case TARGET_INVALID -> State.RECOVERY;
				default -> State.PREPARE_AMBUSH;
			};
			case SUBMERGED -> switch (event) {
				case SUBMERGE_COMPLETE -> State.EMERGE;
				case TARGET_INVALID, NO_SAFE_EMERGE -> State.RECOVERY;
				default -> State.SUBMERGED;
			};
			case EMERGE -> switch (event) {
				case EMERGE_BINDABLE -> State.BIND;
				case EMERGE_INVALID, TARGET_INVALID, NO_SAFE_EMERGE -> State.RECOVERY;
				default -> State.EMERGE;
			};
			case BIND -> State.BIND;
			case RELEASE -> event == Event.RELEASE_COMPLETE ? State.RECOVERY : State.RELEASE;
			case RECOVERY -> event == Event.RECOVERY_COMPLETE ? State.FOLLOW_READY : State.RECOVERY;
		};
	}

	private static boolean isReleaseEvent(Event event) {
		return switch (event) {
			case BIND_TIMER, BIND_LEASH, MANUAL_RECALL, SERPENT_DEATH, SERPENT_REMOVED,
					SERPENT_TEARDOWN, OWNER_DEATH, OWNER_DISCONNECT, OWNER_RESPAWN,
					OWNER_DIMENSION_CHANGE, VICTIM_DEATH, VICTIM_DISCONNECT,
					VICTIM_DIMENSION_CHANGE, VICTIM_UNLOAD, VICTIM_INELIGIBLE,
					HOLD_OWNERSHIP_MISMATCH, SERVER_TEARDOWN, DESELECTED, FIXTURE_RESET -> true;
			default -> false;
		};
	}

	private static boolean finite(Vec3 value) {
		return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
	}
}
