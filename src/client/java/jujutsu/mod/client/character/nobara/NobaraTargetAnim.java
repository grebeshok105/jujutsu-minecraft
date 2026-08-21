package jujutsu.mod.client.character.nobara;

import jujutsu.mod.client.ui.UiEase;

/**
 * Pure animation math for the Nobara target HUD. The only project import is {@link UiEase}.
 *
 * <p>The HUD animates from {@code client.level.getGameTime() + partialTick}; all public methods take
 * a tick age (whole ticks) plus the current frame's partial tick.
 */
public final class NobaraTargetAnim {
	private NobaraTargetAnim() {}

	public static final float APPEAR_TICKS = 3f;
	public static final float POP_TICKS = 3f;
	public static final float PULSE_TICKS = 4f;
	public static final float SLIDE_PX = 6f;

	/** Card opacity, easing 0 {@literal ->} 1 over {@link #APPEAR_TICKS} ticks (outCubic). */
	public static float appearAlpha(long ageTicks, float partialTick) {
		return UiEase.outCubic((ageTicks + partialTick) / APPEAR_TICKS);
	}

	/** Horizontal slide-in offset: {@link #SLIDE_PX} px at t=0, 0 once settled. */
	public static float slideOffsetPx(long ageTicks, float partialTick) {
		return (1f - appearAlpha(ageTicks, partialTick)) * SLIDE_PX;
	}

	/**
	 * Overshoot scale shown on the nails card when the nail count changes: an outBack overshoot of
	 * up to 18% decaying over {@link #POP_TICKS} ticks; exactly 1 once the window lapses.
	 */
	public static float popScale(long sinceChangeTicks, float partialTick) {
		float t = (sinceChangeTicks + partialTick) / POP_TICKS;
		if (t >= 1f) {
			return 1f;
		}
		return 1f + 0.18f * (UiEase.outBack(t) - 1f) * (1f - t);
	}

	/** Health-card border highlight: 1 at change time, 0 after {@link #PULSE_TICKS} ticks. */
	public static float pulseAlpha(long sinceChangeTicks, float partialTick) {
		return 1f - UiEase.clamp01((sinceChangeTicks + partialTick) / PULSE_TICKS);
	}

	/** Frame-rate independent chaser toward a target health value. */
	public static float approachValue(float current, float target, float deltaTicks) {
		return UiEase.approach(current, target, 0.30f, deltaTicks);
	}
}
