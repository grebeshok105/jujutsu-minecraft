package jujutsu.mod.client.fx;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import jujutsu.mod.fx.HairpinTimeline;

public final class HairpinCinematicCamera {
	private static final List<Impulse> IMPULSES = new ArrayList<>();

	private HairpinCinematicCamera() {}

	public static void trigger(HairpinTimeline.Phase phase) {
		switch (phase) {
			case PREP_FREEZE -> addImpulse(180, 0.18f, -0.12f, 18.0f);
			case HAMMER_SNAP -> addImpulse(110, -0.55f, 0.34f, 27.0f);
			case NAIL_IGNITION -> addImpulse(220, 0.25f, -0.18f, 20.0f);
			case HAIRPIN_BLOOM -> addImpulse(260, 1.35f, -0.72f, 34.0f);
			case AFTERGLOW -> addImpulse(420, -0.20f, 0.12f, 11.0f);
			case DONE -> {
			}
		}
	}

	public static float yawOffset() {
		return sample(true);
	}

	public static float pitchOffset() {
		return sample(false);
	}

	public static float fovOffset() {
		return Math.max(-4.0f, Math.min(8.0f, Math.abs(sample(true)) * 2.5f + Math.abs(sample(false)) * 1.5f));
	}

	private static void addImpulse(int durationMillis, float yawAmplitude, float pitchAmplitude, float frequency) {
		IMPULSES.add(new Impulse(System.currentTimeMillis(), durationMillis, yawAmplitude, pitchAmplitude, frequency));
	}

	private static float sample(boolean yaw) {
		long now = System.currentTimeMillis();
		float value = 0.0f;
		Iterator<Impulse> iterator = IMPULSES.iterator();
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
}
