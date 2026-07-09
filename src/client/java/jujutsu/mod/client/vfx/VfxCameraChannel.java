package jujutsu.mod.client.vfx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class VfxCameraChannel {
	private final List<Impulse> impulses = new ArrayList<>();
	private final List<FovImpulse> fovImpulses = new ArrayList<>();

	public void triggerSwing(int intensity, float proximity) {
		float strength = strength(intensity, proximity, 0.92f);
		addImpulse(190, -2.9f * strength, 1.72f * strength, 72.0f);
		addImpulse(95, 1.18f * strength, -0.86f * strength, 126.0f);
		addFovImpulse(430, -7.5f * strength, 0.16f);
	}

	public void triggerImpact(int intensity, float proximity) {
		float strength = strength(intensity, proximity, 1.08f);
		addImpulse(270, 3.3f * strength, -2.1f * strength, 58.0f);
		addImpulse(130, -1.52f * strength, 1.18f * strength, 118.0f);
		addFovImpulse(520, 9.0f * strength, 0.10f);
		addFovImpulse(950, -2.6f * strength, 0.34f);
	}

	public float yawOffset() {
		return sample(true);
	}

	public float pitchOffset() {
		return sample(false);
	}

	public float fovOffset() {
		float shake = Math.max(-5.0f, Math.min(13.0f, Math.abs(sample(true)) * 3.4f + Math.abs(sample(false)) * 2.0f));
		return Math.max(-18.0f, Math.min(20.0f, shake + sampleFov()));
	}

	void clear() {
		impulses.clear();
		fovImpulses.clear();
	}

	private void addImpulse(int durationMillis, float yawAmplitude, float pitchAmplitude, float frequency) {
		impulses.add(new Impulse(System.currentTimeMillis(), durationMillis, yawAmplitude, pitchAmplitude, frequency));
	}

	private void addFovImpulse(int durationMillis, float amplitude, float attackFraction) {
		fovImpulses.add(new FovImpulse(System.currentTimeMillis(), durationMillis, amplitude, Math.max(0.02f, Math.min(0.9f, attackFraction))));
	}

	private float sampleFov() {
		long now = System.currentTimeMillis();
		float value = 0.0f;
		Iterator<FovImpulse> iterator = fovImpulses.iterator();
		while (iterator.hasNext()) {
			FovImpulse impulse = iterator.next();
			long elapsed = now - impulse.startedAtMillis();
			if (elapsed < 0L || elapsed >= impulse.durationMillis()) {
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
