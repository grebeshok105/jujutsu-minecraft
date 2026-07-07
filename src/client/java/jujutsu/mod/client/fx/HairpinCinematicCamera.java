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
			case HAMMER_SNAP -> addImpulse(130, -1.25f, 0.72f, 44.0f);
			case NAIL_IGNITION -> addImpulse(180, 0.58f, -0.38f, 36.0f);
			case HAIRPIN_BLOOM -> addImpulse(280, 2.35f, -1.32f, 48.0f);
			case AFTERGLOW -> addImpulse(420, -0.20f, 0.12f, 11.0f);
			case DONE -> {
			}
		}
	}

	public static void triggerProjectJjkHammer(int nailCount, float proximity) {
		float strength = projectJjkStrength(nailCount, proximity, 0.92f);
		addImpulse(190, -2.9f * strength, 1.72f * strength, 72.0f);
		addImpulse(95, 1.18f * strength, -0.86f * strength, 126.0f);
	}

	public static void triggerProjectJjkImpact(int nailCount, float proximity) {
		float strength = projectJjkStrength(nailCount, proximity, 1.08f);
		addImpulse(270, 3.3f * strength, -2.1f * strength, 58.0f);
		addImpulse(130, -1.52f * strength, 1.18f * strength, 118.0f);
	}

	public static float yawOffset() {
		return sample(true);
	}

	public static float pitchOffset() {
		return sample(false);
	}

	public static float fovOffset() {
		return Math.max(-5.0f, Math.min(13.0f, Math.abs(sample(true)) * 3.4f + Math.abs(sample(false)) * 2.0f));
	}

	private static void addImpulse(int durationMillis, float yawAmplitude, float pitchAmplitude, float frequency) {
		IMPULSES.add(new Impulse(System.currentTimeMillis(), durationMillis, yawAmplitude, pitchAmplitude, frequency));
	}

	private static float projectJjkStrength(int nailCount, float proximity, float multiplier) {
		float countScale = 0.84f + Math.min(8, Math.max(1, nailCount)) * 0.055f;
		return Math.max(0.0f, Math.min(1.65f, proximity * countScale * multiplier));
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
