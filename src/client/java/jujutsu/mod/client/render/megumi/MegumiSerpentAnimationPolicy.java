package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** Pure Great Serpent clip choice: presentation phase, action layer, then travel. */
public final class MegumiSerpentAnimationPolicy {
	private MegumiSerpentAnimationPolicy() {}

	public enum Clip {
		RISE,
		SINK,
		IDLE,
		SLITHER,
		ACTION
	}

	public static Clip serpent(MegumiShikigamiPresentationPolicy.Phase phase,
			boolean moving, boolean actionActive) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (actionActive) {
			return Clip.ACTION;
		}
		return moving ? Clip.SLITHER : Clip.IDLE;
	}
}
