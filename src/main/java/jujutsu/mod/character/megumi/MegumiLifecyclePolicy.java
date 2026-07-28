package jujutsu.mod.character.megumi;

/** Pure decisions that keep planned teardown callbacks from becoming final-loss events. */
final class MegumiLifecyclePolicy {
	private MegumiLifecyclePolicy() {}

	static ReconcileAction reconcileAction(
			boolean teardownInProgress, boolean packPresent, int livingDogCount) {
		if (teardownInProgress || !packPresent) {
			return ReconcileAction.IGNORE;
		}
		return MegumiSummonState.retainsPack(livingDogCount)
				? ReconcileAction.RETAIN
				: ReconcileAction.FINAL_LOSS;
	}

	static boolean shouldApplyTeardownCooldown(boolean packRecordExisted, boolean foundOwnedDog) {
		return packRecordExisted || foundOwnedDog;
	}

	static DogCleanupAction dogCleanupAction(boolean manualRecall, boolean belongsToRemovedPack) {
		return manualRecall && belongsToRemovedPack
				? DogCleanupAction.BEGIN_RECALL
				: DogCleanupAction.HARD_DISCARD;
	}

	static boolean dogOwnsTeardownCooldown(MegumiDogPresentationPolicy.Phase phase) {
		return phase != MegumiDogPresentationPolicy.Phase.RECALLING;
	}

	enum ReconcileAction {
		IGNORE,
		RETAIN,
		FINAL_LOSS
	}

	enum DogCleanupAction {
		BEGIN_RECALL,
		HARD_DISCARD
	}
}
