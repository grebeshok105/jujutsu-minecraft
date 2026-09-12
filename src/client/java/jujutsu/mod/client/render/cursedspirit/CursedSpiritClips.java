package jujutsu.mod.client.render.cursedspirit;

import net.minecraft.client.animation.KeyframeAnimation;

/**
 * One clip layer per frame, highest priority first: scream, then attack, then locomotion (the idle
 * loop plus the procedural walk).
 *
 * <p>The ported models used to apply every layer in sequence on the same bones, so the eternal
 * looping idle — applied last — overwrote the one-shot poses whenever the clips shared a bone. That
 * is what made bodies jitter and what made a Greater's slam read as an empty swing (issue #77).
 * Keep the precedence here, not in the nine models.
 */
public final class CursedSpiritClips {
	private CursedSpiritClips() {
	}

	/**
	 * Applies exactly one layer for this frame. {@code scream} is null for the variants the pack
	 * authored without a scream channel (Gulber, Guzzler); {@code walk} is null for the glider
	 * (FloatingCurse ships no walk clip at all).
	 */
	public static void apply(CursedSpiritRenderState state, KeyframeAnimation scream, KeyframeAnimation attack,
			KeyframeAnimation idle, KeyframeAnimation walk, float walkSpeedFactor, float walkBounceFactor) {
		if (scream != null && state.scream.isStarted()) {
			scream.apply(state.scream, state.ageInTicks);
			return;
		}
		if (state.attack.isStarted()) {
			attack.apply(state.attack, state.ageInTicks);
			return;
		}
		idle.apply(state.idle, state.ageInTicks);
		if (walk != null) {
			walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, walkSpeedFactor, walkBounceFactor);
		}
	}
}
