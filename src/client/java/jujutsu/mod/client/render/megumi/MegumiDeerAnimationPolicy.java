package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** Pure Round Deer clip arbitration: presentation phase, pulse action, then locomotion. */
public final class MegumiDeerAnimationPolicy {
	private MegumiDeerAnimationPolicy() {}

	public enum Clip {
		RISE,
		SINK,
		IDLE,
		WALK,
		PULSE
	}

	public static Clip choose(MegumiShikigamiPresentationPolicy.Phase phase,
			boolean moving, boolean pulseActive) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (pulseActive) {
			return Clip.PULSE;
		}
		return moving ? Clip.WALK : Clip.IDLE;
	}
}
