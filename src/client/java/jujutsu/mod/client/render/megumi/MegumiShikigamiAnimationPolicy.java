package jujutsu.mod.client.render.megumi;

import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;

/** Pure clip choice for the new shikigami bodies. No Minecraft imports beyond the phase. */
public final class MegumiShikigamiAnimationPolicy {
	/** Horizontal speed above which the body reads as travelling fast, in blocks per tick, squared. */
	private static final double RUN_VELOCITY_THRESHOLD_SQR = 0.02;
	/** Swing progress above which the melee layer takes the body; {@code LivingEntity#getAttackAnim} spans 0..1. */
	private static final float ATTACK_ANIM_THRESHOLD = 0.01f;

	private MegumiShikigamiAnimationPolicy() {}

	public static boolean isRunning(double velocityX, double velocityZ) {
		return velocityX * velocityX + velocityZ * velocityZ > RUN_VELOCITY_THRESHOLD_SQR;
	}

	/** Swing progress above which the melee layer takes the body. */
	public static boolean isAttacking(float attackAnim) {
		return attackAnim > ATTACK_ANIM_THRESHOLD;
	}
	/** Rabbit attack trigger: the server action window or the synchronized vanilla swing state. */
	public static boolean rabbitsAction(boolean actionWindow, float attackAnim) {
		return actionWindow || isAttacking(attackAnim);
	}

	/** Keep the imported rabbit attack readable after the short vanilla swing window closes. */
	public static boolean rabbitAttackOwnsClip(boolean triggerActive, boolean clipPlaying,
			boolean clipFinished) {
		return triggerActive || (clipPlaying && !clipFinished);
	}

	/** A new rabbit bump restarts a clip that has already completed. */
	public static boolean rabbitAttackNeedsRestart(boolean triggerActive, boolean clipPlaying,
			boolean clipFinished) {
		return triggerActive && clipPlaying && clipFinished;
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

	/**
	 * Toad priority: phase (rise/sink) > action (tongue or swing — the animatable disambiguates by the
	 * action timer) > walk. The imported set authors no idle clip, so a resting toad reports IDLE and
	 * the renderer stops the controller on the bind pose.
	 */
	public static Clip toad(MegumiShikigamiPresentationPolicy.Phase phase, boolean moving,
			int actionTicks, float attackAnim) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (actionTicks > 0 || attackAnim > ATTACK_ANIM_THRESHOLD) {
			return Clip.ACTION;
		}
		return moving ? Clip.FLY : Clip.IDLE;
	}

	/** Rabbit Escape priority: phase > movement; the swarm has no action clip of its own. */
	public static Clip rabbits(MegumiShikigamiPresentationPolicy.Phase phase, boolean moving, boolean running) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (!moving) {
			return Clip.IDLE;
		}
		return running ? Clip.RUN : Clip.FLY;
	}

	/**
	 * Max Elephant priority: phase > action (the trunk jet or a swing, disambiguated by the action
	 * timer) > travel. The animatable upgrades FLY to the run cycle at speed.
	 */
	public static Clip elephant(MegumiShikigamiPresentationPolicy.Phase phase, boolean moving,
			boolean actionActive, float attackAnim) {
		if (phase == MegumiShikigamiPresentationPolicy.Phase.MATERIALIZING) {
			return Clip.RISE;
		}
		if (phase == MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return Clip.SINK;
		}
		if (actionActive || attackAnim > ATTACK_ANIM_THRESHOLD) {
			return Clip.ACTION;
		}
		return moving ? Clip.FLY : Clip.IDLE;
	}
}
