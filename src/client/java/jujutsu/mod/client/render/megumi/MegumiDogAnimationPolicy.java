package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiDogPresentationPolicy;

/** Pure clip choice for one Divine Dog body. No Minecraft imports beyond the phase. */
public final class MegumiDogAnimationPolicy {
	/** Horizontal speed above which the dog reads as running, in blocks per tick, squared. */
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.02;
	/** Swing progress above which the attack clip wins; {@code LivingEntity#getAttackAnim} spans 0..1. */
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
