package jujutsu.mod.client.vfx;

import java.util.function.LongSupplier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import jujutsu.mod.vfx.VfxTimeline;

/**
 * First-person hand transform channel.
 * SNAP = Nobara-style one-sided hammer snap (legacy).
 * CLAP = Todo Boogie Woogie dual-hand meet/release (deterministic, always from progress 0).
 * SIGN = Megumi dual-hand canine sign (deterministic, always from progress 0).
 * <p>
 * CLAP offsets must stay small: they are applied as a parent of {@code renderPlayerArm},
 * which itself does large fixed translates (e.g. ~5.6). Big parent rotations throw hands off-screen.
 */
public final class VfxFirstPersonChannel {
	public enum Style {
		SNAP,
		CLAP,
		SIGN
	}

	/** Kept for ProjectSanity / Nobara SNAP timing contract. */
	private static final float DURATION_SECONDS = 0.75f;
	private static final float SNAP_DURATION_SECONDS = DURATION_SECONDS;
	private static final float CLAP_DURATION_SECONDS = 0.72f;
	private static final float SIGN_DURATION_SECONDS = 0.80f;
	/** Visual palm contact for clap (must match TP ability.boogie_woogie contact). */
	public static final float CLAP_CONTACT_PROGRESS = 0.39f;

	private long startedAtNanos = Long.MIN_VALUE;
	private Style style = Style.SNAP;
	private final LongSupplier nanoTime;

	public VfxFirstPersonChannel() {
		this(System::nanoTime);
	}

	VfxFirstPersonChannel(LongSupplier nanoTime) {
		this.nanoTime = nanoTime;
	}

	public void triggerSnap() {
		triggerSnap(0.0f);
	}

	public void triggerSnap(float initialAgeTicks) {
		// One channel serves every vessel: never reinterpret an in-flight clap with snap timing.
		if (style == Style.CLAP && progress() >= 0.0f) {
			return;
		}
		style = Style.SNAP;
		startedAtNanos = VfxTimeline.startedAtNanos(nanoTime.getAsLong(), initialAgeTicks);
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
		startedAtNanos = nanoTime.getAsLong();
	}

	public void triggerSign() {
		triggerSign(0.0f);
	}

	/** Starts the confirmed summon sign from zero and ignores duplicate cues while it is active. */
	public void triggerSign(float initialAgeTicks) {
		if (style == Style.SIGN) {
			float progress = rawProgress();
			if (progress >= 0.0f && progress < 1.0f) {
				return;
			}
		}
		style = Style.SIGN;
		// Intentionally ignore initialAgeTicks: a late/replayed cue must not seek the local hand pose.
		startedAtNanos = nanoTime.getAsLong();
	}

	/** Null once the animation is over, so a finished style never keeps cancelling the vanilla hand path. */
	public Style style() {
		return progress() < 0.0f ? null : style;
	}

	/**
	 * Pure read: -1 when idle or finished, otherwise 0..1. Callers that render must sample this
	 * once per frame and reuse the value, or the two arms land on different instants.
	 */
	public float progress() {
		float progress = rawProgress();
		return progress >= 1.0f ? -1.0f : progress;
	}

	/** Releases a finished animation. Call once per frame, never from a pose getter. */
	public void expireIfFinished() {
		if (rawProgress() >= 1.0f) {
			startedAtNanos = Long.MIN_VALUE;
		}
	}

	/** Drops an in-flight animation, e.g. when the player switches vessel mid-clap. */
	public void cancel() {
		startedAtNanos = Long.MIN_VALUE;
		style = Style.SNAP;
	}

	private float rawProgress() {
		if (startedAtNanos == Long.MIN_VALUE) {
			return -1.0f;
		}
		float duration = switch (style) {
			case SNAP -> SNAP_DURATION_SECONDS;
			case CLAP -> CLAP_DURATION_SECONDS;
			case SIGN -> SIGN_DURATION_SECONDS;
		};
		return (nanoTime.getAsLong() - startedAtNanos) / 1_000_000_000.0f / duration;
	}

	public Pose currentPose() {
		float progress = progress();
		if (progress < 0.0f || style != Style.SNAP) {
			return null;
		}
		return snapPose(progress);
	}

	/** Pose at a caller-supplied progress, so both arms of one frame share the same instant. */
	public Pose dualArmPose(Style requestedStyle, HumanoidArm arm, float progress) {
		if (progress < 0.0f || style != requestedStyle) {
			return null;
		}
		return switch (requestedStyle) {
			case CLAP -> clapPose(progress, arm);
			case SIGN -> signPose(progress, arm);
			case SNAP -> null;
		};
	}

	void clear() {
		cancel();
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

	private static Pose signPose(float progress, HumanoidArm arm) {
		float riseEnd = 0.25f;
		float holdEnd = 0.72f;
		float amount;
		if (progress < riseEnd) {
			amount = easeOutCubic(progress / riseEnd);
		} else if (progress < holdEnd) {
			amount = 1.0f;
		} else {
			amount = 1.0f - easeInCubic((progress - holdEnd) / (1.0f - holdEnd));
		}
		float inward = -0.105f * amount;
		float raise = Mth.lerp(amount, 0.055f, 0.205f);
		float forward = Mth.lerp(amount, 0.0f, -0.045f);
		float pitch = -10.0f * amount;
		float yaw = 13.0f * amount;
		float roll = -12.0f * amount;
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
