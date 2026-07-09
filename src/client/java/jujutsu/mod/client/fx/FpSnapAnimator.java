package jujutsu.mod.client.fx;

import net.minecraft.util.Mth;
import org.joml.Vector3f;

public final class FpSnapAnimator {
	private static final float DURATION_SECONDS = 0.5f;
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
		float scaled = progress * 10.0f;
		Vector3f translate = new Vector3f(1.25f, -0.8f, 0.0f);
		Vector3f rotate = new Vector3f(50.0f, 70.0f, -20.0f);
		if (scaled < 1.0f) {
			return new Pose(translate, rotate);
		}
		if (scaled < 4.0f) {
			float phase = (scaled - 1.0f) / 3.0f;
			float eased = phase * phase * phase * phase;
			translate.set(1.4f, Mth.lerp(eased, -0.8f, -0.3f), 0.0f);
			rotate.set(Mth.lerp(eased, 50.0f, 20.0f), 70.0f, -20.0f);
			return new Pose(translate, rotate);
		}
		if (scaled < 8.0f) {
			translate.set(1.25f, -0.3f, 0.0f);
			rotate.set(20.0f, 70.0f, -20.0f);
			return new Pose(translate, rotate);
		}
		float phase = (scaled - 8.0f) / 2.0f;
		float eased = phase * phase * phase;
		translate.set(1.25f, Mth.lerp(eased, -0.3f, -1.2f), 0.0f);
		rotate.set(20.0f, 70.0f, -20.0f);
		return new Pose(translate, rotate);
	}

	public record Pose(Vector3f translate, Vector3f rotate) {}
}
