package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** Pure clip choice for one Divine Dog body. No Minecraft imports beyond the phase. */
public final class MegumiDogAnimationPolicy {
	private MegumiDogAnimationPolicy() {}

	public enum Clip {
		IDLE,
		WALK,
		SPRINT,
		ATTACK,
		RISE,
		SINK
	}

	/** Priority: phase (rise/sink) > attack > sprint > walk > idle. */
	public static Clip decide(MegumiDogPresentationPolicy.Phase phase, boolean moving, boolean running, boolean attacking) {
		if (phase == MegumiDogPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiDogPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (attacking) {
			return Clip.ATTACK;
		}
		if (!moving) {
			return Clip.IDLE;
		}
		return running ? Clip.SPRINT : Clip.WALK;
	}
}
