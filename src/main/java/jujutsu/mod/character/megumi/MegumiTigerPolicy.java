package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Pure gates and transitions for Tiger Funeral's authorial, grounded three-beat combo. */
final class MegumiTigerPolicy {
	private MegumiTigerPolicy() {}

	static boolean canStart(ComboStartFacts facts) {
		return facts.active()
				&& facts.cooldownReady()
				&& facts.targetAlive()
				&& !facts.targetRemoved()
				&& facts.targetEligible()
				&& facts.withinApproachRange()
				&& facts.inStrikeArc();
	}

	/**
	 * The action timer counts down from windup + the final authored beat window. The strike windows
	 * are offsets from the end of windup, so all three clips have deterministic, non-overlapping beats.
	 */
	static boolean hitWindowReached(State state, int actionTicks) {
		return switch (state) {
			case COMBO_WINDUP -> actionTicks <= MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK;
			case STRIKE_1 -> actionTicks <= MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK
					- MegumiShikigamiProfile.TIGER_STRIKE1_WINDOW_TICK;
			case STRIKE_2 -> actionTicks <= MegumiShikigamiProfile.TIGER_FINISHER_WINDOW_TICK
					- MegumiShikigamiProfile.TIGER_STRIKE2_WINDOW_TICK;
			case FINISHER -> actionTicks <= 0;
			default -> false;
		};
	}

	/**
	 * Checks horizontal range and facing against the nearest point on the target box. Vertical
	 * tolerance is the closest vertical separation between the attacker's feet and that box.
	 */
	static boolean inStrikeArc(Vec3 attackerPos, Vec3 forward, AABB targetBox,
			double range, double minForwardDot, double verticalTolerance) {
		if (!Double.isFinite(range) || range < 0.0
				|| !Double.isFinite(minForwardDot)
				|| !Double.isFinite(verticalTolerance) || verticalTolerance < 0.0) {
			return false;
		}
		double nearestX = clamp(attackerPos.x, targetBox.minX, targetBox.maxX);
		double nearestY = clamp(attackerPos.y, targetBox.minY, targetBox.maxY);
		double nearestZ = clamp(attackerPos.z, targetBox.minZ, targetBox.maxZ);
		double dx = nearestX - attackerPos.x;
		double dy = nearestY - attackerPos.y;
		double dz = nearestZ - attackerPos.z;
		if (Math.abs(dy) > verticalTolerance || dx * dx + dz * dz > range * range) {
			return false;
		}

		double forwardLength = Math.sqrt(forward.x * forward.x + forward.z * forward.z);
		if (forwardLength < 1.0E-8) {
			return false;
		}
		double targetLength = Math.sqrt(dx * dx + dz * dz);
		if (targetLength < 1.0E-8) {
			// An overlapping target box has no meaningful nearest-point direction; use its center
			// solely for the arc check while retaining nearest-point range and vertical checks.
			dx = targetBox.getCenter().x - attackerPos.x;
			dz = targetBox.getCenter().z - attackerPos.z;
			targetLength = Math.sqrt(dx * dx + dz * dz);
			if (targetLength < 1.0E-8) {
				return minForwardDot <= 1.0;
			}
		}
		double dot = (forward.x * dx + forward.z * dz) / (forwardLength * targetLength);
		return dot >= minForwardDot;
	}

	static StrikeOutcome strikeOutcome(StrikeFacts facts) {
		if (!facts.targetExists() || !facts.targetAlive() || facts.targetRemoved() || !facts.targetEligible()) {
			return StrikeOutcome.CANCEL;
		}
		return facts.inStrikeArc() ? StrikeOutcome.HIT : StrikeOutcome.MISS;
	}

	static State nextState(State state, Event event) {
		return switch (state) {
			case STALK_APPROACH -> event == Event.COMBO_START ? State.COMBO_WINDUP : state;
			case COMBO_WINDUP -> switch (event) {
				case WINDUP_COMPLETE -> State.STRIKE_1;
				case TARGET_INVALID -> State.RECOVERY;
				default -> state;
			};
			case STRIKE_1 -> switch (event) {
				case STRIKE_COMPLETE -> State.STRIKE_2;
				case TARGET_INVALID -> State.RECOVERY;
				default -> state;
			};
			case STRIKE_2 -> switch (event) {
				case STRIKE_COMPLETE -> State.FINISHER;
				case TARGET_INVALID -> State.RECOVERY;
				default -> state;
			};
			case FINISHER -> switch (event) {
				case FINISHER_COMPLETE, TARGET_INVALID -> State.RECOVERY;
				default -> state;
			};
			case RECOVERY -> event == Event.RECOVERY_COMPLETE ? State.STALK_APPROACH : state;
		};
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	record ComboStartFacts(boolean active, boolean cooldownReady,
			boolean targetAlive, boolean targetRemoved, boolean targetEligible,
			boolean withinApproachRange, boolean inStrikeArc) {}

	record StrikeFacts(boolean targetExists, boolean targetAlive, boolean targetRemoved,
			boolean targetEligible, boolean inStrikeArc) {}

	enum State {
		STALK_APPROACH,
		COMBO_WINDUP,
		STRIKE_1,
		STRIKE_2,
		FINISHER,
		RECOVERY
	}

	enum Event {
		COMBO_START,
		WINDUP_COMPLETE,
		STRIKE_COMPLETE,
		FINISHER_COMPLETE,
		TARGET_INVALID,
		TARGET_MOVED,
		NEW_TARGET,
		RECOVERY_COMPLETE
	}

	enum StrikeOutcome {
		HIT,
		MISS,
		CANCEL
	}
}
