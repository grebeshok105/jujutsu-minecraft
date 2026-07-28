package jujutsu.mod.character.megumi;

/** Pure phase timing and combat decisions for one transient Divine Dog body. */
public final class MegumiDogPresentationPolicy {
	private MegumiDogPresentationPolicy() {}

	public static Phase phaseAfterTick(Phase phase, int phaseTicks) {
		if (phase == Phase.MATERIALIZING && phaseTicks >= MegumiProfile.DOG_MATERIALIZATION_TICKS) {
			return Phase.ACTIVE;
		}
		return phase;
	}

	public static boolean recallComplete(int phaseTicks) {
		return phaseTicks >= MegumiProfile.DOG_RECALL_TICKS;
	}

	public static boolean combatEnabled(Phase phase) {
		return phase == Phase.ACTIVE;
	}

	public static float progress(Phase phase, int phaseTicks, float partialTick) {
		if (phase == Phase.ACTIVE) {
			return 1.0f;
		}
		int duration = phase == Phase.MATERIALIZING
				? MegumiProfile.DOG_MATERIALIZATION_TICKS
				: MegumiProfile.DOG_RECALL_TICKS;
		return clamp((phaseTicks + partialTick) / duration);
	}

	public static float verticalOffset(Phase phase, float progress) {
		float clampedProgress = clamp(progress);
		return switch (phase) {
			case MATERIALIZING -> clampedProgress - 1.0f;
			case ACTIVE -> 0.0f;
			case RECALLING -> -clampedProgress;
		};
	}

	private static float clamp(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	public enum Phase {
		MATERIALIZING,
		ACTIVE,
		RECALLING;

		public int networkId() {
			return ordinal();
		}

		public static Phase fromNetworkId(int networkId) {
			return switch (networkId) {
				case 0 -> MATERIALIZING;
				case 1 -> ACTIVE;
				case 2 -> RECALLING;
				default -> MATERIALIZING;
			};
		}
	}
}
