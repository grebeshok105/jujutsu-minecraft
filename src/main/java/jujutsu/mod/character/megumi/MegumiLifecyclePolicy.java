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

	enum ReconcileAction {
		IGNORE,
		RETAIN,
		FINAL_LOSS
	}
}
