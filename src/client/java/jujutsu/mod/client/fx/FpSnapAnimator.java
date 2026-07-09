package jujutsu.mod.client.fx;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class FpSnapAnimator {
	private static final float DURATION_SECONDS = 0.36f;
	private static long startedAtNanos = Long.MIN_VALUE;

	private FpSnapAnimator() {}

	public static void playSnap() {
		startedAtNanos = System.nanoTime();
	}

	public static Pose currentPose() {
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

	private static Pose pose(float progress) {
		float windup = progress < 0.36f ? progress / 0.36f : 1.0f;
		float release = progress < 0.36f ? 0.0f : Math.min(1.0f, (progress - 0.36f) / 0.64f);
		float snap = (float) Math.sin(windup * Math.PI);
		float recover = 1.0f - release * release;
		Vector3f translate = new Vector3f(
				0.08f + 0.1f * snap,
				Mth.lerp(snap, 0.0f, -0.055f) * recover,
				-0.08f - 0.16f * snap * recover
		);
		Vector3f rotate = new Vector3f(
				-4.0f + 18.0f * snap * recover,
				7.0f * snap * recover,
				-16.0f * snap * recover
		);
		return new Pose(translate, rotate);
	}

	public record Pose(Vector3f translate, Vector3f rotate) {}
}
