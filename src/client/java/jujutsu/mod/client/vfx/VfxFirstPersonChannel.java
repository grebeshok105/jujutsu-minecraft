package jujutsu.mod.client.vfx;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import jujutsu.mod.vfx.VfxTimeline;

/**
 * First-person hand transform channel.
 * SNAP = Nobara-style one-sided hammer snap (legacy).
 * CLAP = Todo Boogie Woogie dual-hand meet/release (deterministic, always from progress 0).
 * <p>
 * CLAP offsets must stay small: they are applied as a parent of {@code renderPlayerArm},
 * which itself does large fixed translates (e.g. ~5.6). Big parent rotations throw hands off-screen.
 */
public final class VfxFirstPersonChannel {
	public enum Style {
		SNAP,
		CLAP
	}

	/** Kept for ProjectSanity / Nobara SNAP timing contract. */
	private static final float DURATION_SECONDS = 0.75f;
	private static final float SNAP_DURATION_SECONDS = DURATION_SECONDS;
	private static final float CLAP_DURATION_SECONDS = 0.72f;
	/** Visual palm contact for clap (must match TP ability.boogie_woogie contact). */
	public static final float CLAP_CONTACT_PROGRESS = 0.39f;

	private long startedAtNanos = Long.MIN_VALUE;
	private Style style = Style.SNAP;

	public void triggerSnap() {
		triggerSnap(0.0f);
	}

	public void triggerSnap(float initialAgeTicks) {
		style = Style.SNAP;
		startedAtNanos = VfxTimeline.startedAtNanos(System.nanoTime(), initialAgeTicks);
	}

	public void triggerClap() {
		triggerClap(0.0f);
	}

	/**
	 * Always starts at progress 0. Ignores late-cue age. Ignores re-triggers while already playing.
	 */
	public void triggerClap(float initialAgeTicks) {
		if (style == Style.CLAP) {
			float progress = rawProgress();
			if (progress >= 0.0f && progress < 0.97f) {
				return;
			}
		}
		style = Style.CLAP;
		// Intentionally ignore initialAgeTicks: FP clap must be frame-stable and identical every cast.
		startedAtNanos = System.nanoTime();
	}

	public Style style() {
		return rawProgress() < 0.0f ? null : style;
	}

	public float activeProgress() {
		float progress = rawProgress();
		if (progress >= 1.0f) {
			startedAtNanos = Long.MIN_VALUE;
			return -1.0f;
		}
		return progress;
	}

	private float rawProgress() {
		if (startedAtNanos == Long.MIN_VALUE) {
			return -1.0f;
		}
		float duration = style == Style.CLAP ? CLAP_DURATION_SECONDS : SNAP_DURATION_SECONDS;
		return (System.nanoTime() - startedAtNanos) / 1_000_000_000.0f / duration;
	}

	public Pose currentPose() {
		float progress = activeProgress();
		if (progress < 0.0f || style != Style.SNAP) {
			return null;
		}
		return snapPose(progress);
	}

	public Pose clapArmPose(HumanoidArm arm) {
		float progress = activeProgress();
		if (progress < 0.0f || style != Style.CLAP) {
			return null;
		}
		return clapPose(progress, arm);
	}

	void clear() {
		startedAtNanos = Long.MIN_VALUE;
	}

	private static Pose snapPose(float progress) {
		float scaledProgress = progress * 15.0f;
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

	/**
	 * Screen-safe dual clap. Magnitudes stay near SNAP scale so parent-space multiplies
	 * with renderPlayerArm's large fixed translates stay on-camera.
	 * <p>
	 * Mixin applies: translateX*side, rotateY*side, rotateZ*side — both palms move inward.
	 */
	private static Pose clapPose(float progress, HumanoidArm arm) {
		float windupEnd = 0.18f;
		float contact = CLAP_CONTACT_PROGRESS;
		float holdEnd = 0.52f;
		float meet;
		if (progress < windupEnd) {
			meet = easeOutCubic(progress / windupEnd) * 0.45f;
		} else if (progress < contact) {
			float t = (progress - windupEnd) / (contact - windupEnd);
			meet = Mth.lerp(easeInCubic(t), 0.45f, 1.0f);
		} else if (progress < holdEnd) {
			meet = 1.0f;
		} else {
			float t = (progress - holdEnd) / (1.0f - holdEnd);
			meet = 1.0f - easeOutCubic(t);
		}

		// Screen-safe dual meet. Keep parent rotations small (renderPlayerArm uses ~5.6u base translates).
		// Stronger raise so BOTH arms sit clearly in the lower FOV rather than under the bezel.
		float inward = -0.12f * meet;
		float raise = Mth.lerp(meet, 0.06f, 0.20f);
		float forward = Mth.lerp(meet, 0.0f, -0.05f);
		float pitch = Mth.lerp(meet, 0.0f, -8.0f);
		float yaw = 16.0f * meet;
		float roll = -6.0f * meet;
		return new Pose(inward, raise, forward, pitch, yaw, roll);
	}

	private static float easeInCubic(float t) {
		return t * t * t;
	}

	private static float easeOutCubic(float t) {
		float u = 1.0f - t;
		return 1.0f - u * u * u;
	}

	public record Pose(float translateX, float translateY, float translateZ, float rotateX, float rotateY, float rotateZ) {}
}
