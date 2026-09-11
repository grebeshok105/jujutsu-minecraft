package jujutsu.mod.character.megumi;

/**
 * Pure phase timing and combat decisions for one transient shikigami body. The parameterized
 * durations keep this policy type-agnostic: each body supplies its own materialization/recall
 * windows from {@link MegumiShikigamiProfile} — the Divine Dog policy stays untouched.
 */
public final class MegumiShikigamiPresentationPolicy {
	private MegumiShikigamiPresentationPolicy() {}

	public static Phase phaseAfterTick(Phase phase, int phaseTicks, int materializeTicks) {
		if (phase == Phase.MATERIALIZING && phaseTicks >= materializeTicks) {
			return Phase.ACTIVE;
		}
		return phase;
	}

	public static boolean recallComplete(int phaseTicks, int recallTicks) {
		return phaseTicks >= recallTicks;
	}

	public static boolean combatEnabled(Phase phase) {
		return phase == Phase.ACTIVE;
	}

	public static float progress(Phase phase, int phaseTicks, float partialTick, int materializeTicks, int recallTicks) {
		if (phase == Phase.ACTIVE) {
			return 1.0f;
		}
		int duration = phase == Phase.MATERIALIZING ? materializeTicks : recallTicks;
		return clamp((phaseTicks + partialTick) / (float) duration);
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