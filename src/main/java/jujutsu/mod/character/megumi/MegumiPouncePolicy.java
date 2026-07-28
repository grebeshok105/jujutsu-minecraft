package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/** Pure server gate for one dog's Sic-only pounce. */
final class MegumiPouncePolicy {
	private MegumiPouncePolicy() {}

	static boolean canLaunch(LaunchFacts facts) {
		return facts.active()
				&& facts.currentPack()
				&& facts.validOwner()
				&& facts.currentTargetMatches()
				&& facts.eligible()
				&& facts.lineOfSight()
				&& facts.distance() >= MegumiProfile.POUNCE_MIN_RANGE
				&& facts.distance() <= MegumiProfile.POUNCE_MAX_RANGE
				&& facts.deadlineReady();
	}

	static boolean deadlineReady(long gameTime, long nextReadyGameTime) {
		return gameTime >= nextReadyGameTime;
	}

	static boolean timedOut(long gameTime, long deadlineGameTime) {
		return gameTime > deadlineGameTime;
	}

	static Vec3 launchVelocity(Vec3 dogPosition, Vec3 targetPosition) {
		Vec3 horizontal = targetPosition.subtract(dogPosition).multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return Vec3.ZERO;
		}
		double vertical = Math.max(MegumiProfile.POUNCE_VERTICAL_SPEED,
				Math.min(MegumiProfile.POUNCE_MAX_VERTICAL_SPEED,
						MegumiProfile.POUNCE_VERTICAL_SPEED + (targetPosition.y - dogPosition.y) * 0.10));
		return horizontal.normalize().scale(MegumiProfile.POUNCE_HORIZONTAL_SPEED).add(0.0, vertical, 0.0);
	}

	static Vec3 steerVelocity(Vec3 currentVelocity, Vec3 dogPosition, Vec3 targetPosition) {
		Vec3 horizontal = targetPosition.subtract(dogPosition).multiply(1.0, 0.0, 1.0);
		if (horizontal.lengthSqr() < 1.0E-6) {
			return currentVelocity;
		}
		return horizontal.normalize().scale(MegumiProfile.POUNCE_HORIZONTAL_SPEED)
				.add(0.0, currentVelocity.y, 0.0);
	}

	static FlightAction flightAction(
			boolean horizontalCollision, boolean verticalCollision, boolean onGround, int elapsedTicks) {
		return horizontalCollision || verticalCollision || elapsedTicks > 0 && onGround
				? FlightAction.FINISH_POUNCE
				: FlightAction.CONTINUE;
	}

	static InFlightAction inFlightAction(InFlightFacts facts) {
		if (!facts.active()
				|| !facts.currentPack()
				|| !facts.validOwner()
				|| !facts.assignedSicTarget()
				|| !facts.currentTargetMatches()
				|| !facts.eligible()) {
			return InFlightAction.CLEAR_SIC;
		}
		if (!facts.pounceTargetMatches() || facts.timedOut()) {
			return InFlightAction.FINISH_POUNCE;
		}
		return InFlightAction.CONTINUE;
	}

	record LaunchFacts(
			boolean active,
			boolean currentPack,
			boolean validOwner,
			boolean currentTargetMatches,
			boolean eligible,
			boolean lineOfSight,
			double distance,
			boolean deadlineReady) {}

	record InFlightFacts(
			boolean active,
			boolean currentPack,
			boolean validOwner,
			boolean assignedSicTarget,
			boolean currentTargetMatches,
			boolean eligible,
			boolean pounceTargetMatches,
			boolean timedOut) {}

	enum InFlightAction {
		CLEAR_SIC,
		FINISH_POUNCE,
		CONTINUE
	}

	enum FlightAction {
		FINISH_POUNCE,
		CONTINUE
	}
}
