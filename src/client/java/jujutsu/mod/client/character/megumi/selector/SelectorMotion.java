package jujutsu.mod.client.character.megumi.selector;

import java.util.Arrays;
import jujutsu.mod.client.ui.UiEase;

/**
 * Presentation-only animation state for the shikigami quick selector (issue #109).
 *
 * <p>Every channel is a pure projection of a wall-clock deadline: {@link #open()}, {@link #close()},
 * {@link #pulse(int)} and {@link #shake(int)} only record {@code System.currentTimeMillis()} and the
 * getters compute the current value on demand. Nothing here blocks, sleeps or queues, which is what
 * keeps the spec's "animation never makes the selector slower" promise: a click landing mid-pulse
 * simply restarts that slot's clock, and a player hammering the strip faster than 140 ms per click
 * still gets a fresh response to every click instead of a backlog of canned sequences.
 *
 * <p>Durations come from {@link SelectorTheme} so palette and timing are tuned in one place. The
 * only channel driven per frame instead of per clock is the hover scale, which {@link #tick(float)}
 * approaches toward its target; pass how far the cursor has entered the slot via
 * {@link #hoverScale(int, boolean)}.
 */
public final class SelectorMotion {
	/** Milliseconds a slot trails the one before it on entrance. */
	public static final float SLOT_STAGGER_MS = 12f;
	/** Reject shake length and its peak horizontal excursion (spec: short and restrained). */
	public static final float SHAKE_MS = 90f;
	public static final float SHAKE_MAX_PX = 2f;
	/** Two damped swings inside the shake window: enough to read as a refusal, not as a wobble. */
	private static final float SHAKE_SWINGS = 2f;
	/**
	 * Floor on a trailing slot's own animation window, as a share of the entrance. Without it a ten-entry
	 * strip would spend its whole budget on delays and snap the last slots in with no motion at all.
	 */
	private static final float MIN_SLOT_WINDOW_SHARE = 0.35f;
	/** Hover model emphasis band: the slot the cursor is inside grows by at most 8%. */
	public static final float HOVER_SCALE_MIN = 1.0f;
	public static final float HOVER_SCALE_MAX = 1.08f;
	/** Hover gap share closed per tick, derived so {@code HOVER_MS} is the ~63% settling time. */
	private static final float HOVER_APPROACH = 1f - (float) Math.exp(-50f / SelectorTheme.HOVER_MS);

	private long stripStartMs = System.currentTimeMillis();
	private float stripFrom;
	private float stripTo;
	private float stripMs = 1f;

	private float[] hoverScale = new float[0];
	private boolean[] hoverTarget = new boolean[0];
	private long[] pulseStartMs = new long[0];
	private long[] shakeStartMs = new long[0];

	/** Starts (or restarts) the entrance, continuing from wherever a half-faded strip stood. */
	public void open() {
		beginPhase(1f, SelectorTheme.OPEN_MS);
	}

	/** Starts the exit from the current progress; a strip that barely made it in leaves just as fast. */
	public void close() {
		beginPhase(0f, SelectorTheme.CLOSE_MS);
	}

	/** 0 = fully out (translated down, faded), 1 = fully in. The slide itself is the caller's offset. */
	public float stripProgress() {
		return stripFrom + (stripTo - stripFrom) * UiEase.outCubic(elapsedSince(stripStartMs) / stripMs);
	}

	/**
	 * This slot's own eased entrance/exit progress, delayed by {@link #SLOT_STAGGER_MS} per index so the
	 * strip unrolls left to right. Usable directly as the slot's alpha factor and, as {@code 1 - value},
	 * as its slide-in offset. Each slot keeps at least {@link #MIN_SLOT_WINDOW_SHARE} of the entrance for
	 * its own motion, so even the last entry of a ten-entry strip animates instead of snapping in; the
	 * exit runs unstaggered so closing stays inside the 100 ms budget.
	 */
	public float slotStagger(int index) {
		if (index < 0) {
			return stripProgress();
		}
		float delay = stripFrom < stripTo ? index * SLOT_STAGGER_MS : 0f;
		float window = Math.max(stripMs - delay, stripMs * MIN_SLOT_WINDOW_SHARE);
		float eased = UiEase.outCubic((elapsedSince(stripStartMs) - delay) / window);
		return stripFrom + (stripTo - stripFrom) * eased;
	}

	/**
	 * Current hover scale for the slot; {@code hovered} latches the target the next {@link #tick} pulls
	 * toward. Reading without ticking returns the settled value, never a half-updated one.
	 */
	public float hoverScale(int index, boolean hovered) {
		if (index < 0) {
			return HOVER_SCALE_MIN;
		}
		ensureSlot(index);
		hoverTarget[index] = hovered;
		return hoverScale[index];
	}

	/** Advances the per-frame channels. Clock-driven channels need no tick -- they read the wall clock. */
	public void tick(float deltaTicks) {
		float delta = Float.isFinite(deltaTicks) ? Math.max(0f, deltaTicks) : 0f;
		for (int i = 0; i < hoverScale.length; i++) {
			float target = hoverTarget[i] ? HOVER_SCALE_MAX : HOVER_SCALE_MIN;
			hoverScale[i] = UiEase.approach(hoverScale[i], target, HOVER_APPROACH, delta);
		}
	}

	/** Starts the accent ring on a slot; a later click on the same slot restarts it rather than stacking. */
	public void pulse(int index) {
		if (index < 0) {
			return;
		}
		ensureSlot(index);
		pulseStartMs[index] = System.currentTimeMillis();
	}

	/** Starts the reject shake on a slot. */
	public void shake(int index) {
		if (index < 0) {
			return;
		}
		ensureSlot(index);
		shakeStartMs[index] = System.currentTimeMillis();
	}

	/**
	 * Remaining strength of the slot's select pulse: 1 on the frame the click lands, decaying to 0 over
	 * {@link SelectorTheme#SELECT_MS}, and 0 whenever no pulse is running. Consumers draw the flash with
	 * it directly ({@code alpha = pulseT * k}) and can use {@code 1 - pulseT} when they want the ring's
	 * growth instead.
	 */
	public float pulseT(int index) {
		float progress = index < 0 ? 1f : progressSince(pulseStartMs, index, SelectorTheme.SELECT_MS);
		return progress >= 1f ? 0f : 1f - progress;
	}

	/** Horizontal offset in pixels, damped from {@link #SHAKE_MAX_PX} to 0 over {@link #SHAKE_MS}. */
	public float shakeOffset(int index) {
		float t = index < 0 ? 1f : progressSince(shakeStartMs, index, SHAKE_MS);
		if (t >= 1f) {
			return 0f;
		}
		return (float) Math.sin(t * Math.PI * 2.0 * SHAKE_SWINGS) * (1f - t) * SHAKE_MAX_PX;
	}

	private void beginPhase(float to, float durationMs) {
		float from = stripProgress();
		stripFrom = from;
		stripTo = to;
		stripMs = Math.max(1f, durationMs * Math.max(0.05f, Math.abs(to - from)));
		stripStartMs = System.currentTimeMillis();
	}

	private static float elapsedSince(long startMs) {
		return System.currentTimeMillis() - startMs;
	}

	private static float progressSince(long[] starts, int index, float durationMs) {
		if (index >= starts.length || starts[index] <= 0L) {
			return 1f; // never fired: callers read that as "nothing to draw"
		}
		return UiEase.clamp01(elapsedSince(starts[index]) / Math.max(1f, durationMs));
	}

	private void ensureSlot(int index) {
		if (index < hoverScale.length) {
			return;
		}
		int old = hoverScale.length;
		int size = Math.max(8, Integer.highestOneBit(index + 1) * 2);
		hoverScale = Arrays.copyOf(hoverScale, size);
		hoverTarget = Arrays.copyOf(hoverTarget, size);
		pulseStartMs = Arrays.copyOf(pulseStartMs, size);
		shakeStartMs = Arrays.copyOf(shakeStartMs, size);
		Arrays.fill(hoverScale, old, size, HOVER_SCALE_MIN);
	}
}
