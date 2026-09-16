package jujutsu.mod.client.vfx.domain;

/**
 * Pure-math timeline of a single domain sphere: expand, hold, fade.
 *
 * <p>The record deliberately carries no Minecraft types. Easing decisions live here, not in GLSL:
 * the channel hands the shader a final {@code radius}/{@code fade}/{@code progress} per frame, so
 * the shape of the expansion is testable without a GL context.
 *
 * <p>Durations are clamped into a non-degenerate range by the canonical constructor: a zero-length
 * expand or fade window would make progress ratios produce NaN, and NaN reaching the uniform buffer
 * turns into an invisible-but-still-drawn sphere, which is the hardest kind of failure to diagnose.
 *
 * @param expandTicks ticks from radius ~0 to {@code maxRadius} (easeOutQuart)
 * @param holdTicks ticks at full radius after the expansion
 * @param fadeTicks ticks of the final fade-out
 * @param maxRadius world-space radius reached at the end of the expansion
 */
public record DomainSphereTiming(int expandTicks, int holdTicks, int fadeTicks, double maxRadius) {
	public static final int DEFAULT_EXPAND_TICKS = 20;
	public static final int DEFAULT_HOLD_TICKS = 220;
	public static final int DEFAULT_FADE_TICKS = 20;

	public DomainSphereTiming {
		expandTicks = Math.max(1, expandTicks);
		holdTicks = Math.max(0, holdTicks);
		fadeTicks = Math.max(1, fadeTicks);
		maxRadius = Double.isFinite(maxRadius) ? Math.max(0.0, maxRadius) : 0.0;
	}

	public static DomainSphereTiming defaults(double maxRadius) {
		return new DomainSphereTiming(DEFAULT_EXPAND_TICKS, DEFAULT_HOLD_TICKS, DEFAULT_FADE_TICKS, maxRadius);
	}

	public int totalTicks() {
		return expandTicks + holdTicks + fadeTicks;
	}

	/**
	 * Radius at {@code ageTicks}: easeOutQuart, i.e. {@code maxRadius * (1 - (1 - t)^4)} with
	 * {@code t} the clamped expansion progress. Fast start, smoothly decelerating, flat at the top —
	 * {@code 0.9375 * maxRadius} already at the midpoint of the expansion.
	 */
	public double radiusAt(float ageTicks) {
		double progress = expansionProgress(ageTicks);
		double remaining = 1.0 - progress;
		return maxRadius * (1.0 - remaining * remaining * remaining * remaining);
	}

	public float expansionProgress(float ageTicks) {
		return clamp01(ageTicks / expandTicks);
	}

	/** Whole-lifetime progress, used for the breathing tint — the shader needs a 0..1 plateau for it. */
	public float ageProgress(float ageTicks) {
		return clamp01(ageTicks / totalTicks());
	}

	/**
	 * Opacity multiplier: 1.0 through expansion and hold, then a smoothstep mirror falling to 0.0
	 * over {@code fadeTicks} (zero slope at both ends, so the disappearance has no visible kink).
	 */
	public float fadeAt(float ageTicks) {
		float fadeStartTicks = expandTicks + holdTicks;
		if (ageTicks <= fadeStartTicks) {
			return 1.0f;
		}
		float fadeProgress = clamp01((ageTicks - fadeStartTicks) / fadeTicks);
		return 1.0f - fadeProgress * fadeProgress * (3.0f - 2.0f * fadeProgress);
	}

	public boolean isExpired(float ageTicks) {
		return ageTicks >= totalTicks();
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}
}
