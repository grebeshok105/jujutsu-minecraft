package jujutsu.mod.client.vfx;

import jujutsu.mod.vfx.VfxTimeline;

/**
 * Small client-only render-time dilation for confirmed cinematic impacts.
 * It never changes server ticks or gameplay state.
 */
public final class VfxTimeChannel {
	private long activeUntilMillis;
	private float activeScale = 1.0f;

	public void triggerSlowMotion(float scale, int durationMillis, float initialAgeTicks) {
		if (durationMillis <= 0) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now >= activeUntilMillis) {
			activeScale = 1.0f;
		}
		long startedAt = VfxTimeline.startedAtMillis(now, initialAgeTicks);
		long expiresAt = startedAt + durationMillis;
		if (expiresAt <= now) {
			return;
		}
		activeScale = Math.min(activeScale, clampScale(scale));
		activeUntilMillis = Math.max(activeUntilMillis, expiresAt);
	}

	public float timeScale() {
		if (System.currentTimeMillis() >= activeUntilMillis) {
			activeUntilMillis = 0L;
			activeScale = 1.0f;
			return 1.0f;
		}
		return activeScale;
	}

	void clear() {
		activeUntilMillis = 0L;
		activeScale = 1.0f;
	}

	static float clampScale(float scale) {
		return Math.max(0.45f, Math.min(1.0f, scale));
	}
}
