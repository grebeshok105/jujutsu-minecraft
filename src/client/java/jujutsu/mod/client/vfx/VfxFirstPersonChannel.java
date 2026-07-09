package jujutsu.mod.client.vfx;

import net.minecraft.util.Mth;

public final class VfxFirstPersonChannel {
	private static final float DURATION_SECONDS = 0.75f;
	private long startedAtNanos = Long.MIN_VALUE;

	public void triggerSnap() {
		startedAtNanos = System.nanoTime();
	}

	public Pose currentPose() {
		if (startedAtNanos == Long.MIN_VALUE) {
			return null;
		}
		float progress = (System.nanoTime() - startedAtNanos) / 1_000_000_000.0f / DURATION_SECONDS;
		if (progress >= 1.0f) {
			startedAtNanos = Long.MIN_VALUE;
			return null;
		}
		return pose(progress);
	}

	void clear() {
		startedAtNanos = Long.MIN_VALUE;
	}

	private static Pose pose(float progress) {
		float scaledProgress = progress * 10.0f;
		if (scaledProgress < 1.0f) {
			return new Pose(0.13f, -0.07f, -0.06f, 13.0f, 18.0f, -8.0f);
		}
		if (scaledProgress < 4.0f) {
			float phase = (scaledProgress - 1.0f) / 3.0f;
			float easeInQuint = (float) Math.pow(phase, 4.0);
			return new Pose(0.15f, Mth.lerp(easeInQuint, -0.07f, -0.025f), -0.07f, Mth.lerp(easeInQuint, 13.0f, 5.5f), 18.0f, -8.0f);
		}
		if (scaledProgress < 8.0f) {
			return new Pose(0.135f, -0.025f, -0.07f, 5.5f, 18.0f, -8.0f);
		}
		if (scaledProgress < 15.0f) {
			float phase = (scaledProgress - 8.0f) / 7.0f;
			float easeInCubic = phase * phase * phase;
			return new Pose(
					Mth.lerp(easeInCubic, 0.135f, 0.08f),
					Mth.lerp(easeInCubic, -0.025f, -0.105f),
					Mth.lerp(easeInCubic, -0.07f, -0.11f),
					Mth.lerp(easeInCubic, 5.5f, -2.0f),
					Mth.lerp(easeInCubic, 18.0f, 6.0f),
					Mth.lerp(easeInCubic, -8.0f, -14.0f)
			);
		}
		return new Pose(0.08f, -0.105f, -0.11f, -2.0f, 6.0f, -14.0f);
	}

	public record Pose(float translateX, float translateY, float translateZ, float rotateX, float rotateY, float rotateZ) {}
}
