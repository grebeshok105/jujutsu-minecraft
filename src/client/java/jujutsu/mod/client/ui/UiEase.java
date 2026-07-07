package jujutsu.mod.client.ui;

/** Easing functions + a tiny spring-ish value chaser for smooth UI animation. */
public final class UiEase {
	private UiEase() {}

	public static float clamp01(float t) {
		return t < 0.0f ? 0.0f : Math.min(1.0f, t);
	}

	public static float outCubic(float t) {
		t = clamp01(t);
		float inv = 1.0f - t;
		return 1.0f - inv * inv * inv;
	}

	public static float inOutCubic(float t) {
		t = clamp01(t);
		return t < 0.5f ? 4.0f * t * t * t : 1.0f - (float) Math.pow(-2.0 * t + 2.0, 3) / 2.0f;
	}

	public static float outBack(float t) {
		t = clamp01(t);
		float c1 = 1.70158f;
		float c3 = c1 + 1.0f;
		float inv = t - 1.0f;
		return 1.0f + c3 * inv * inv * inv + c1 * inv * inv;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * clamp01(t);
	}

	/** Frame-rate independent approach toward a target. */
	public static float approach(float current, float target, float speed, float deltaTicks) {
		float factor = 1.0f - (float) Math.pow(1.0f - clamp01(speed), Math.max(0.0f, deltaTicks));
		return current + (target - current) * factor;
	}
}
