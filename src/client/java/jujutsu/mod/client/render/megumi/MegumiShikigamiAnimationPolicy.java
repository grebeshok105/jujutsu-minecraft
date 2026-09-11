package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** Pure clip choice for the new shikigami bodies. No Minecraft imports beyond the phase. */
public final class MegumiShikigamiAnimationPolicy {
	/** Horizontal speed above which the body reads as travelling fast, in blocks per tick, squared. */
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.02;

	private MegumiShikigamiAnimationPolicy() {}

	public static boolean isRunning(double velocityX, double velocityZ) {
		return velocityX * velocityX + velocityZ * velocityZ > RUN_VELOCITY_THRESHOLD_SQR;
	}

	public enum Clip {
		/** Rising out of the shadow pool. */
		RISE,
		/** Sinking back into it. */
		SINK,
		/** Hovering in place. */
		IDLE,
		/** Travelling at patrol speed. */
		FLY,
		/** Travelling fast (the dive). */
		RUN,
		/** One-shot action: the dive impact, the tongue, the jet. */
		ACTION
	}

	/**
	 * Nue priority: phase (rise/sink) > action > travel. The action clip is whole-body in the imported
	 * Nue set, so it deliberately outranks the flight cycle instead of layering on top of it.
	 */
	public static Clip nue(MegumiShikigamiPresentationPolicy.Phase phase, boolean moving, boolean running,
			boolean actionActive) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (actionActive) {
			return Clip.ACTION;
		}
		if (!moving) {
			return Clip.IDLE;
		}
		return running ? Clip.RUN : Clip.FLY;
	}
}
