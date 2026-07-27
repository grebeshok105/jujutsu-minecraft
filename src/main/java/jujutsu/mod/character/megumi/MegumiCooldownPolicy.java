package jujutsu.mod.character.megumi;

/** Pure cooldown duration and non-shortening rules for the Divine Dog pack. */
final class MegumiCooldownPolicy {
	private MegumiCooldownPolicy() {}

	static int duration(Cause cause) {
		return switch (cause) {
			case NONE -> 0;
			case RECALL -> MegumiProfile.RECALL_COOLDOWN_TICKS;
			case FINAL_LOSS -> MegumiProfile.PACK_DEATH_COOLDOWN_TICKS;
		};
	}

	static int preservedRemaining(int currentRemaining, int requestedDuration) {
		return Math.max(currentRemaining, requestedDuration);
	}

	enum Cause {
		NONE,
		RECALL,
		FINAL_LOSS
	}
}
