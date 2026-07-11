package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jujutsu.mod.vfx.VfxTimeline;

public final class VfxCameraChannel {
	private static final int MAX_CHANNEL_IMPULSES = 64;
	private final List<Impulse> impulses = new ArrayList<>();
	private final List<FovImpulse> fovImpulses = new ArrayList<>();

	public void triggerLaunch(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(System.currentTimeMillis(), initialAgeTicks);
		float strength = strength(intensity, proximity, 0.92f);
		addImpulse(startedAtMillis, 170, -2.6f * strength, 1.25f * strength, 76.0f);
		addImpulse(startedAtMillis + 70L, 110, 1.15f * strength, -0.72f * strength, 128.0f);
		addFovImpulse(startedAtMillis, 250, -8.0f * strength, 0.12f);
		addFovImpulse(startedAtMillis + 130L, 330, 3.6f * strength, 0.18f);
	}

	public void triggerHeavyImpact(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(System.currentTimeMillis(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.08f);
		addImpulse(startedAtMillis, 245, 3.7f * strength, -2.35f * strength, 62.0f);
		addImpulse(startedAtMillis + 85L, 150, -1.7f * strength, 1.25f * strength, 122.0f);
		addFovImpulse(startedAtMillis, 175, -4.8f * strength, 0.08f);
		addFovImpulse(startedAtMillis + 75L, 510, 10.5f * strength, 0.12f);
	}

	public void triggerExplosion(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(System.currentTimeMillis(), initialAgeTicks);
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
		long startedAtMillis = VfxTimeline.startedAtMillis(System.currentTimeMillis(), initialAgeTicks);
		float strength = strength(intensity, proximity, 0.98f);
		addImpulse(startedAtMillis, 310, -1.4f * strength, 1.05f * strength, 48.0f);
		addImpulse(startedAtMillis + 110L, 150, 2.4f * strength, -1.65f * strength, 116.0f);
		addFovImpulse(startedAtMillis, 230, -9.5f * strength, 0.18f);
		addFovImpulse(startedAtMillis + 150L, 430, 5.4f * strength, 0.12f);
	}

	public void triggerResonanceImpact(int intensity, float proximity, float initialAgeTicks) {
		long startedAtMillis = VfxTimeline.startedAtMillis(System.currentTimeMillis(), initialAgeTicks);
		float strength = strength(intensity, proximity, 1.16f);
		addImpulse(startedAtMillis, 360, 5.2f * strength, -3.35f * strength, 54.0f);
		addImpulse(startedAtMillis + 92L, 230, -2.8f * strength, 1.9f * strength, 98.0f);
		addImpulse(startedAtMillis + 188L, 150, 1.35f * strength, -0.92f * strength, 136.0f);
		addFovImpulse(startedAtMillis, 190, -10.8f * strength, 0.08f);
		addFovImpulse(startedAtMillis + 118L, 560, 6.9f * strength, 0.16f);
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

	void clear() {
		impulses.clear();
		fovImpulses.clear();
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
		long now = System.currentTimeMillis();
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

	private float sample(boolean yaw) {
		long now = System.currentTimeMillis();
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
}
