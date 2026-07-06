package jujutsu.mod.client.fx;

import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.fx.HairpinTimeline;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;

public final class HairpinPlayback {
	private final HairpinFxPayload payload;
	private final long startedAtMillis;
	private final Random random;
	private HairpinTimeline.Phase lastPhase = HairpinTimeline.Phase.DONE;

	public HairpinPlayback(HairpinFxPayload payload, long startedAtMillis) {
		this.payload = payload;
		this.startedAtMillis = startedAtMillis;
		this.random = new Random(payload.seed());
	}

	public long elapsedMillis(long currentTimeMillis) {
		return Math.max(0L, currentTimeMillis - startedAtMillis);
	}

	public HairpinTimeline.Phase phase(long currentTimeMillis) {
		return HairpinTimeline.phaseAt(elapsedMillis(currentTimeMillis));
	}

	public float progressInPhase(long currentTimeMillis) {
		return HairpinTimeline.progressInPhase(elapsedMillis(currentTimeMillis));
	}

	public boolean isDone(long currentTimeMillis) {
		return phase(currentTimeMillis) == HairpinTimeline.Phase.DONE;
	}

	public void tick(Minecraft client, long currentTimeMillis) {
		if (client.level == null) {
			return;
		}

		HairpinTimeline.Phase phase = phase(currentTimeMillis);
		if (phase != lastPhase) {
			playPhaseSound(client.level, phase);
			lastPhase = phase;
		}
		spawnPhaseParticles(client.level, phase, progressInPhase(currentTimeMillis));
	}

	public int seed() {
		return payload.seed();
	}

	public Vec3 target() {
		return new Vec3(payload.targetX(), payload.targetY(), payload.targetZ());
	}

	public List<Vec3> nails() {
		return List.of(
				new Vec3(payload.nail0X(), payload.nail0Y(), payload.nail0Z()),
				new Vec3(payload.nail1X(), payload.nail1Y(), payload.nail1Z()),
				new Vec3(payload.nail2X(), payload.nail2Y(), payload.nail2Z()),
				new Vec3(payload.nail3X(), payload.nail3Y(), payload.nail3Z())
		);
	}

	private void playPhaseSound(ClientLevel level, HairpinTimeline.Phase phase) {
		Vec3 target = target();
		switch (phase) {
			case PREP_FREEZE -> play(level, target, JujutsuSounds.HAIRPIN_PREP, 0.45f, 0.8f);
			case HAMMER_SNAP -> {
				HairpinScreenOverlay.triggerFlash(90, 78);
				play(level, target, JujutsuSounds.HAIRPIN_HAMMER_SNAP, 0.85f, 1.25f);
			}
			case NAIL_IGNITION -> play(level, target, JujutsuSounds.HAIRPIN_NAIL_IGNITE, 0.65f, 1.1f);
			case HAIRPIN_BLOOM -> {
				HairpinScreenOverlay.triggerFlash(150, 132);
				play(level, target, JujutsuSounds.HAIRPIN_BLOOM, 1.0f, 0.95f);
			}
			case AFTERGLOW -> play(level, target, JujutsuSounds.HAIRPIN_AFTERGLOW, 0.35f, 1.35f);
			case DONE -> {
			}
		}
	}

	private void play(ClientLevel level, Vec3 position, net.minecraft.sounds.SoundEvent soundEvent, float volume, float pitch) {
		level.playLocalSound(position.x, position.y, position.z, soundEvent, SoundSource.PLAYERS, volume, pitch, false);
	}

	private void spawnPhaseParticles(ClientLevel level, HairpinTimeline.Phase phase, float progress) {
		switch (phase) {
			case PREP_FREEZE -> spawnNailSparks(level, 1, 0.018);
			case HAMMER_SNAP -> spawnTargetBurst(level, 8, 0.16);
			case NAIL_IGNITION -> {
				spawnNailSparks(level, 2, 0.055);
				spawnTracerSparks(level, progress);
			}
			case HAIRPIN_BLOOM -> spawnTargetBurst(level, 12, 0.24);
			case AFTERGLOW -> spawnTargetBurst(level, 2, 0.07);
			case DONE -> {
			}
		}
	}

	private void spawnNailSparks(ClientLevel level, int countPerNail, double speed) {
		for (Vec3 nail : nails()) {
			for (int index = 0; index < countPerNail; index++) {
				spawnSpark(level, nail, randomVelocity(speed));
			}
		}
	}

	private void spawnTracerSparks(ClientLevel level, float progress) {
		Vec3 target = target();
		for (Vec3 nail : nails()) {
			Vec3 point = nail.lerp(target, progress);
			Vec3 towardTarget = target.subtract(nail).normalize().scale(0.09);
			spawnSpark(level, point, towardTarget.add(randomVelocity(0.015)));
		}
	}

	private void spawnTargetBurst(ClientLevel level, int count, double speed) {
		Vec3 target = target();
		for (int index = 0; index < count; index++) {
			spawnSpark(level, target, randomVelocity(speed));
		}
	}

	private void spawnSpark(ClientLevel level, Vec3 position, Vec3 velocity) {
		level.addParticle(
				JujutsuParticles.HAIRPIN_SPARK,
				position.x,
				position.y,
				position.z,
				velocity.x,
				velocity.y,
				velocity.z
		);
	}

	private Vec3 randomVelocity(double speed) {
		double x = random.nextDouble() - 0.5;
		double y = random.nextDouble() - 0.25;
		double z = random.nextDouble() - 0.5;
		Vec3 velocity = new Vec3(x, y, z);
		if (velocity.lengthSqr() < 1.0E-5) {
			return new Vec3(0.0, speed, 0.0);
		}
		return velocity.normalize().scale(speed * (0.55 + random.nextDouble() * 0.65));
	}
}
