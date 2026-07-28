package jujutsu.mod.character.megumi;

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
}
