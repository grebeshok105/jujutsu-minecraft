package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import jujutsu.mod.client.render.ShadowBodySink;
import jujutsu.mod.vfx.VfxCue;
import jujutsu.mod.vfx.VfxTimeline;

public final class VfxCameraChannel {
	private static final int MAX_CHANNEL_IMPULSES = 64;
	private static final LongSupplier SYSTEM_CLOCK = System::currentTimeMillis;
	private final List<Impulse> impulses = new ArrayList<>();
	private final List<FovImpulse> fovImpulses = new ArrayList<>();
	private final LongSupplier currentTimeMillis;

	// First-person dive: the camera depth follows ShadowBodySink for the camera entity, so this channel
	// stays the single camera authority. The phase machine below is the shared clock for both the
	// camera offset and the HUD veil, so both read the same sink / under / emerge beats.
	private static final int DIVE_UNDER_EASE_TICKS = 3;
	private static final float DIVE_SINK_DEPTH_BLOCKS = 0.85f;
	private static final float DIVE_UNDER_DEPTH_BLOCKS = 0.35f;
	private static final float DIVE_EMERGE_START_DEPTH_BLOCKS = 0.5f;
	private static final float DIVE_FADE_SINK_MAX_ALPHA = 0.75f;
	private static final float DIVE_FADE_UNDER_HOLD_ALPHA = 0.25f;
	private static final float DIVE_FADE_EMERGE_START_ALPHA = 0.45f;
	private static final int NO_DIVE_ENTITY = Integer.MIN_VALUE;

	private int diveEntityId = NO_DIVE_ENTITY;
	private DivePhase divePhase = DivePhase.NONE;
	private long diveUnderSinceGameTime;
	private float diveUnderEase;
	private float diveSinkProgress = -1.0f;
	private float diveEmergeProgress = -1.0f;

	public VfxCameraChannel() {
		this(SYSTEM_CLOCK);
	}

	VfxCameraChannel(LongSupplier currentTimeMillis) {
		this.currentTimeMillis = currentTimeMillis;
	}

	public void triggerLaunch(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 0.92f);
		addImpulse(startedAtMillis, 170, -2.6f * strength, 1.25f * strength, 76.0f);
		addImpulse(startedAtMillis + 70L, 110, 1.15f * strength, -0.72f * strength, 128.0f);
		addFovImpulse(startedAtMillis, 250, -8.0f * strength, 0.12f);
		addFovImpulse(startedAtMillis + 130L, 330, 3.6f * strength, 0.18f);
	}

	public void triggerHeavyImpact(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.08f);
		addImpulse(startedAtMillis, 245, 3.7f * strength, -2.35f * strength, 62.0f);
		addImpulse(startedAtMillis + 85L, 150, -1.7f * strength, 1.25f * strength, 122.0f);
		addFovImpulse(startedAtMillis, 175, -4.8f * strength, 0.08f);
		addFovImpulse(startedAtMillis + 75L, 510, 10.5f * strength, 0.12f);
	}

	public void triggerExplosion(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.0f);
		for (int index = 0; index < 3; index++) {
			long offset = index * 58L;
			float direction = (index & 1) == 0 ? 1.0f : -1.0f;
			addImpulse(startedAtMillis + offset, 105, 2.3f * strength * direction, -1.55f * strength, 148.0f + index * 24.0f);
		}
		addFovImpulse(startedAtMillis, 145, -5.5f * strength, 0.08f);
		addFovImpulse(startedAtMillis + 65L, 420, 8.2f * strength, 0.14f);
	}

	public void triggerRitual(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 0.98f);
		addImpulse(startedAtMillis, 310, -1.4f * strength, 1.05f * strength, 48.0f);
		addImpulse(startedAtMillis + 110L, 150, 2.4f * strength, -1.65f * strength, 116.0f);
		addFovImpulse(startedAtMillis, 230, -9.5f * strength, 0.18f);
		addFovImpulse(startedAtMillis + 150L, 430, 5.4f * strength, 0.12f);
	}

	public void triggerResonanceImpact(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.16f);
		addImpulse(startedAtMillis, 360, 5.2f * strength, -3.35f * strength, 54.0f);
		addImpulse(startedAtMillis + 92L, 230, -2.8f * strength, 1.9f * strength, 98.0f);
		addImpulse(startedAtMillis + 188L, 150, 1.35f * strength, -0.92f * strength, 136.0f);
		addFovImpulse(startedAtMillis, 190, -10.8f * strength, 0.08f);
		addFovImpulse(startedAtMillis + 118L, 560, 6.9f * strength, 0.16f);
	}

	public void triggerBlackFlash(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.15f);
		addImpulse(startedAtMillis, 200, 6.5f * strength, -4.0f * strength, 120.0f);
		addImpulse(startedAtMillis + 80L, 240, 2.4f * strength, 1.8f * strength, 135.0f);
		addImpulse(startedAtMillis + 200L, 180, -2.8f * strength, 1.2f * strength, 90.0f);
		addImpulse(startedAtMillis + 400L, 160, 1.5f * strength, -0.9f * strength, 70.0f);
		addFovImpulse(startedAtMillis, 350, -12.0f * strength, 0.22f);
		addFovImpulse(startedAtMillis + 250L, 450, 8.0f * strength, 0.35f);
	}

	/**
	 * A displacement, not a blow. High frequency, short duration and a small FOV dip, so it reads as a body
	 * being moved rather than something exploding: about a third of {@link #triggerHeavyImpact} and a sixth
	 * of {@link #triggerBlackFlash} at the peak, and fully settled inside 240 ms.
	 */
	public void triggerSwapSnap(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(currentTimeMillis.getAsLong(), initialAgeTicks);
		float strength = strength(intensity, proximity, 0.62f);
		addImpulse(startedAtMillis, 95, 2.1f * strength, -1.5f * strength, 158.0f);
		addImpulse(startedAtMillis + 60L, 75, -0.85f * strength, 0.55f * strength, 205.0f);
		addFovImpulse(startedAtMillis, 130, -2.8f * strength, 0.06f);
		addFovImpulse(startedAtMillis + 70L, 170, 1.2f * strength, 0.16f);
	}

	public float yawOffset() {
		return clamp(sample(true), -9.0f, 9.0f);
	}

	public float pitchOffset() {
		return clamp(sample(false), -7.0f, 7.0f);
	}

	public float fovOffset() {
		float shake = Math.max(-5.0f, Math.min(13.0f, Math.abs(sample(true)) * 3.4f + Math.abs(sample(false)) * 2.0f));
		return Math.max(-18.0f, Math.min(20.0f, shake + sampleFov()));
	}

	/**
	 * How far the first-person camera should sink with the diving body, in blocks, or 0 for a fully
	 * vanilla camera. Reads {@link ShadowBodySink} for the camera entity; see {@link #advanceDive(int, long)}.
	 */
	public float diveOffsetBlocks() {
		int entityId = cameraEntityId();
		long gameTime = levelGameTime();
		if (entityId == VfxCue.NO_ANCHOR || gameTime < 0L) {
			return 0.0f;
		}
		return diveOffsetBlocks(entityId, gameTime);
	}

	/** Full-screen veil alpha (0..0.75) for the same dive beats as {@link #diveOffsetBlocks()}. */
	public float diveFadeAlpha() {
		int entityId = cameraEntityId();
		long gameTime = levelGameTime();
		if (entityId == VfxCue.NO_ANCHOR || gameTime < 0L) {
			return 0.0f;
		}
		return diveFadeAlpha(entityId, gameTime);
	}

	/** Test seam: {@link #diveOffsetBlocks()} with explicit tick inputs, no Minecraft needed. */
	float diveOffsetBlocks(int entityId, long gameTime) {
		advanceDive(entityId, gameTime);
		return switch (divePhase) {
			case NONE -> 0.0f;
			case SINKING -> DIVE_SINK_DEPTH_BLOCKS * smoothstep(diveSinkProgress);
			case UNDER -> DIVE_SINK_DEPTH_BLOCKS - (DIVE_SINK_DEPTH_BLOCKS - DIVE_UNDER_DEPTH_BLOCKS) * smoothstep(diveUnderEase);
			case EMERGING -> DIVE_EMERGE_START_DEPTH_BLOCKS * smoothstep(diveEmergeProgress);
		};
	}

	/** Test seam: {@link #diveFadeAlpha()} with explicit tick inputs, no Minecraft needed. */
	float diveFadeAlpha(int entityId, long gameTime) {
		advanceDive(entityId, gameTime);
		return switch (divePhase) {
			case NONE -> 0.0f;
			case SINKING -> DIVE_FADE_SINK_MAX_ALPHA * smoothstep(diveSinkProgress);
			case UNDER -> DIVE_FADE_SINK_MAX_ALPHA - (DIVE_FADE_SINK_MAX_ALPHA - DIVE_FADE_UNDER_HOLD_ALPHA) * smoothstep(diveUnderEase);
			case EMERGING -> DIVE_FADE_EMERGE_START_ALPHA * smoothstep(diveEmergeProgress);
		};
	}

	void clear() {
		impulses.clear();
		fovImpulses.clear();
		diveEntityId = NO_DIVE_ENTITY;
		divePhase = DivePhase.NONE;
		diveUnderSinceGameTime = 0L;
		diveUnderEase = 0.0f;
		diveSinkProgress = -1.0f;
		diveEmergeProgress = -1.0f;
	}

	private void addImpulse(long startedAtMillis, int durationMillis, float yawAmplitude, float pitchAmplitude, float frequency) {
		if (impulses.size() >= MAX_CHANNEL_IMPULSES) {
			impulses.remove(0);
		}
		impulses.add(new Impulse(startedAtMillis, durationMillis, yawAmplitude, pitchAmplitude, frequency));
	}

	private void addFovImpulse(long startedAtMillis, int durationMillis, float amplitude, float attackFraction) {
		if (fovImpulses.size() >= MAX_CHANNEL_IMPULSES) {
			fovImpulses.remove(0);
		}
		fovImpulses.add(new FovImpulse(startedAtMillis, durationMillis, amplitude, Math.max(0.02f, Math.min(0.9f, attackFraction))));
	}

	private float sampleFov() {
		long now = currentTimeMillis.getAsLong();
		float value = 0.0f;
		Iterator<FovImpulse> iterator = fovImpulses.iterator();
		while (iterator.hasNext()) {
			FovImpulse impulse = iterator.next();
			long elapsed = now - impulse.startedAtMillis();
			if (elapsed < 0L) {
				continue;
			}
			if (elapsed >= impulse.durationMillis()) {
				iterator.remove();
				continue;
			}
			float progress = elapsed / (float) impulse.durationMillis();
			float envelope = progress < impulse.attackFraction()
					? progress / impulse.attackFraction()
					: (float) Math.pow(1.0f - (progress - impulse.attackFraction()) / (1.0f - impulse.attackFraction()), 2.0);
			value += impulse.amplitude() * envelope;
		}
		return value;
	}

	private static float strength(int intensity, float proximity, float multiplier) {
		float intensityScale = 0.84f + Math.min(8, Math.max(1, intensity)) * 0.055f;
		return Math.max(0.0f, Math.min(1.65f, proximity * intensityScale * multiplier));
	}

	private static float clamp(float value, float minimum, float maximum) {
		return Math.max(minimum, Math.min(maximum, value));
	}

	private static int cameraEntityId() {
		Minecraft client = Minecraft.getInstance();
		Entity camera = client.cameraEntity != null ? client.cameraEntity : client.player;
		return camera == null ? VfxCue.NO_ANCHOR : camera.getId();
	}

	private static long levelGameTime() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? -1L : level.getGameTime();
	}

	/**
	 * Advances the per-camera-entity dive phase from ShadowBodySink's two progress signals and the
	 * current game time. Idempotent within one game tick, so the camera mixin and the HUD veil may
	 * both sample the channel per frame without double-stepping: sink progress 0..1 over the sink
	 * window, the under hold once the sink completes, then the emerge window (progress 1..0) owns the
	 * rise back to zero.
	 */
	private void advanceDive(int entityId, long gameTime) {
		if (entityId != diveEntityId) {
			diveEntityId = entityId;
			divePhase = DivePhase.NONE;
			diveUnderSinceGameTime = gameTime;
			diveUnderEase = 0.0f;
			diveSinkProgress = -1.0f;
			diveEmergeProgress = -1.0f;
		}
		diveSinkProgress = ShadowBodySink.sinkProgress(entityId, gameTime);
		diveEmergeProgress = ShadowBodySink.emergeProgress(entityId, gameTime);

		// The emerge window always plays out to zero once started, even if the sink query lingers.
		if (divePhase == DivePhase.EMERGING) {
			if (diveEmergeProgress <= 0.0f) {
				divePhase = DivePhase.NONE;
			}
			return;
		}
		if (diveEmergeProgress >= 0.0f) {
			divePhase = DivePhase.EMERGING;
			return;
		}
		if (diveSinkProgress < 0.0f) {
			divePhase = DivePhase.NONE;
			return;
		}
		if (diveSinkProgress < 1.0f) {
			divePhase = DivePhase.SINKING;
			return;
		}
		if (divePhase == DivePhase.UNDER) {
			diveUnderEase = clamp01((float) (gameTime - diveUnderSinceGameTime) / DIVE_UNDER_EASE_TICKS);
			return;
		}
		// First sight of the completed sink: anchor the fast 3-tick glide at the sink bottom. Joining
		// mid-under (a missed sink) is treated as already settled on the hold, never as a fresh bottom.
		diveUnderSinceGameTime = divePhase == DivePhase.SINKING ? gameTime : gameTime - DIVE_UNDER_EASE_TICKS;
		diveUnderEase = divePhase == DivePhase.SINKING ? 0.0f : 1.0f;
		divePhase = DivePhase.UNDER;
	}

	private static float smoothstep(float value) {
		float t = clamp01(value);
		return t * t * (3.0f - 2.0f * t);
	}

	private static float clamp01(float value) {
		return Math.max(0.0f, Math.min(1.0f, value));
	}

	private float sample(boolean yaw) {
		long now = currentTimeMillis.getAsLong();
		float value = 0.0f;
		Iterator<Impulse> iterator = impulses.iterator();
		while (iterator.hasNext()) {
			Impulse impulse = iterator.next();
			long elapsed = now - impulse.startedAtMillis();
			if (elapsed >= impulse.durationMillis()) {
				iterator.remove();
				continue;
			}
			float progress = Math.max(0.0f, elapsed / (float) impulse.durationMillis());
			float decay = (1.0f - progress) * (1.0f - progress);
			float wave = (float) Math.sin(progress * impulse.frequency());
			value += wave * decay * (yaw ? impulse.yawAmplitude() : impulse.pitchAmplitude());
		}
		return value;
	}

	private record Impulse(long startedAtMillis, int durationMillis, float yawAmplitude, float pitchAmplitude, float frequency) {}

	private record FovImpulse(long startedAtMillis, int durationMillis, float amplitude, float attackFraction) {}

	private enum DivePhase {
		NONE, SINKING, UNDER, EMERGING
	}
}
