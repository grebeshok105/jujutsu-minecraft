package jujutsu.mod.client.fx;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class FpSnapAnimator {
	private static final float DURATION_SECONDS = 0.75f;
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
		float scaledProgress = progress * 10.0f;
		Vector3f translate = new Vector3f();
		Vector3f rotate = new Vector3f();
		if (scaledProgress < 1.0f) {
			translate.set(0.13f, -0.07f, -0.06f);
			rotate.set(13.0f, 18.0f, -8.0f);
		} else if (scaledProgress < 4.0f) {
			float phaseProgress = (scaledProgress - 1.0f) / 3.0f;
			float easeInQuint = (float) Math.pow(phaseProgress, 4.0);
			translate.set(0.15f, Mth.lerp(easeInQuint, -0.07f, -0.025f), -0.07f);
			rotate.set(Mth.lerp(easeInQuint, 13.0f, 5.5f), 18.0f, -8.0f);
		} else if (scaledProgress < 8.0f) {
			translate.set(0.135f, -0.025f, -0.07f);
			rotate.set(5.5f, 18.0f, -8.0f);
		} else if (scaledProgress < 15.0f) {
			float phaseProgress = (scaledProgress - 8.0f) / 7.0f;
			float easeInCubic = phaseProgress * phaseProgress * phaseProgress;
			translate.set(
					Mth.lerp(easeInCubic, 0.135f, 0.08f),
					Mth.lerp(easeInCubic, -0.025f, -0.105f),
					Mth.lerp(easeInCubic, -0.07f, -0.11f)
			);
			rotate.set(
					Mth.lerp(easeInCubic, 5.5f, -2.0f),
					Mth.lerp(easeInCubic, 18.0f, 6.0f),
					Mth.lerp(easeInCubic, -8.0f, -14.0f)
			);
		} else {
			translate.set(0.08f, -0.105f, -0.11f);
			rotate.set(-2.0f, 6.0f, -14.0f);
		}
		return new Pose(translate, rotate);
	}

	public record Pose(Vector3f translate, Vector3f rotate) {}
}
