package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** Pure Tiger clip choice: presentation phase outranks action, which outranks travel. */
public final class MegumiTigerAnimationPolicy {
	private MegumiTigerAnimationPolicy() {}

	public static Clip choose(MegumiShikigamiPresentationPolicy.Phase phase,
			boolean moving, boolean actionActive, int comboStep) {
		if (phase != MegumiShikigamiPresentationPolicy.Phase.ACTIVE) {
			return Clip.IDLE;
		}
		if (actionActive) {
			return switch (comboStep) {
				case -1 -> Clip.WINDUP;
				case 1 -> Clip.STRIKE_1;
				case 2 -> Clip.STRIKE_2;
				case 3 -> Clip.FINISHER;
				case 4 -> Clip.RECOVER;
				default -> Clip.IDLE;
			};
		}
		return moving ? Clip.WALK : Clip.IDLE;
	}

	public enum Clip {
		IDLE,
		WALK,
		WINDUP,
		STRIKE_1,
		STRIKE_2,
		FINISHER,
		RECOVER
	}
}
