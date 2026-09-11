package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** Pure clip choice for the Divine Dog body and jaw layers. No Minecraft imports beyond the phase. */
public final class MegumiDogAnimationPolicy {
	/** Horizontal speed above which the dog reads as running, in blocks per tick, squared. */
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.02;
	/** Swing progress above which the bite layer takes the jaw; {@code LivingEntity#getAttackAnim} spans 0..1. */
	private static final float ATTACK_ANIM_THRESHOLD = 0.01f;

	private MegumiDogAnimationPolicy() {}

	/** Running is horizontal: a dog falling or climbing keeps its walk cycle. */
	public static boolean isRunning(double velocityX, double velocityZ) {
		return velocityX * velocityX + velocityZ * velocityZ > RUN_VELOCITY_THRESHOLD_SQR;
	}

	public static boolean isAttacking(float attackAnim) {
		return attackAnim > ATTACK_ANIM_THRESHOLD;
	}

	public enum Clip {
		IDLE,
		WALK,
		SPRINT,
		RISE,
		SINK
	}

	/** Body priority: phase (rise/sink) > sprint > walk > idle. The jaw is a separate layer. */
	public static Clip decide(MegumiDogPresentationPolicy.Phase phase, boolean moving, boolean running) {
		if (phase == MegumiDogPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiDogPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (!moving) {
			return Clip.IDLE;
		}
		return running ? Clip.SPRINT : Clip.WALK;
	}

	/**
	 * The imported bite clip is 0.92 s of jaw work, far longer than the six-tick vanilla swing that starts
	 * it, so the layer keeps the jaw until that clip has played out once instead of cutting it after the
	 * swing window.
	 */
	public static boolean biteOwnsJaw(boolean swinging, boolean clipPlaying, boolean clipFinished) {
		return swinging || (clipPlaying && !clipFinished);
	}

	/** A bite that already played out must be reset, otherwise its controller holds the last frame. */
	public static boolean biteNeedsRestart(boolean swinging, boolean clipPlaying, boolean clipFinished) {
		return swinging && clipPlaying && clipFinished;
	}
}
