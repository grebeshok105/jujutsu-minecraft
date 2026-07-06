package jujutsu.mod.client.fx;

import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.fx.HairpinTimeline;
import jujutsu.mod.fx.HairpinVisualProfile;
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
				HairpinScreenOverlay.triggerFlash(70, 28);
				play(level, target, JujutsuSounds.HAIRPIN_HAMMER_SNAP, 0.85f, 1.25f);
			}
			case NAIL_IGNITION -> play(level, target, JujutsuSounds.HAIRPIN_NAIL_IGNITE, 0.65f, 1.1f);
			case HAIRPIN_BLOOM -> {
				HairpinScreenOverlay.triggerFlash(95, 42);
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
		for (HairpinVisualProfile.ParticleBudget budget : HairpinVisualProfile.budgetsForPhase(phase)) {
			switch (budget.family()) {
				case MARK_STAIN -> spawnNailFamily(level, JujutsuParticles.HAIRPIN_MARK_STAIN, budget.countPerNail(), 0.012);
				case WARN_EDGE -> spawnNailFamily(level, JujutsuParticles.HAIRPIN_WARN_EDGE, budget.countPerNail(), 0.038);
				case COMPRESSION_MOTE -> spawnCompressionMotes(level, budget.countPerNail(), progress);
				case SNAP_CRACK -> spawnSnapCracks(level, budget.countPerNail(), budget.countAtTarget());
				case BURST_RESIDUE -> spawnDirectionalBurst(level, JujutsuParticles.HAIRPIN_BURST_RESIDUE, budget.countPerNail(), budget.countAtTarget(), phase == HairpinTimeline.Phase.AFTERGLOW ? 0.08 : 0.22);
				case BURST_METAL_SHARD -> spawnDirectionalBurst(level, JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, budget.countPerNail(), budget.countAtTarget(), 0.28);
				case IGNITION_TICK -> spawnNailFamily(level, JujutsuParticles.HAIRPIN_IGNITION_TICK, budget.countPerNail(), 0.048);
			}
		}
	}

	private void spawnNailFamily(ClientLevel level, SimpleParticleType type, int countPerNail, double speed) {
		for (Vec3 nail : nails()) {
			for (int index = 0; index < countPerNail; index++) {
				spawnParticle(level, type, jitter(nail, 0.035), randomVelocity(speed));
			}
		}
	}

	private void spawnCompressionMotes(ClientLevel level, int countPerNail, float progress) {
		Vec3 target = target();
		for (Vec3 nail : nails()) {
			Vec3 towardTarget = safeDirection(target.subtract(nail));
			for (int index = 0; index < countPerNail; index++) {
				double lerp = 0.18 + progress * 0.54 + random.nextDouble() * 0.08;
				Vec3 point = jitter(nail.lerp(target, Math.min(0.88, lerp)), 0.045);
				spawnParticle(level, JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, point, towardTarget.scale(0.085).add(randomVelocity(0.012)));
			}
		}
	}

	private void spawnSnapCracks(ClientLevel level, int countPerNail, int countAtTarget) {
		Vec3 target = target();
		for (Vec3 nail : nails()) {
			Vec3 vector = burstVector(nail);
			for (int index = 0; index < countPerNail; index++) {
				spawnParticle(level, JujutsuParticles.HAIRPIN_SNAP_CRACK, nail.lerp(target, 0.72), vector.scale(0.12).add(randomVelocity(0.018)));
			}
		}
		for (int index = 0; index < countAtTarget; index++) {
			spawnParticle(level, JujutsuParticles.HAIRPIN_SNAP_CRACK, jitter(target(), 0.055), randomVelocity(0.08));
		}
	}

	private void spawnDirectionalBurst(ClientLevel level, SimpleParticleType type, int countPerNail, int countAtTarget, double speed) {
		Vec3 target = target();
		for (Vec3 nail : nails()) {
			Vec3 vector = burstVector(nail);
			for (int index = 0; index < countPerNail; index++) {
				Vec3 position = jitter(target.add(vector.scale(0.08 + random.nextDouble() * 0.12)), 0.045);
				Vec3 velocity = vector.scale(speed * (0.65 + random.nextDouble() * 0.55)).add(randomVelocity(speed * 0.22));
				spawnParticle(level, type, position, velocity);
			}
		}
		for (int index = 0; index < countAtTarget; index++) {
			spawnParticle(level, type, jitter(target, 0.06), randomVelocity(speed * 0.7));
		}
	}

	private void spawnParticle(ClientLevel level, SimpleParticleType type, Vec3 position, Vec3 velocity) {
		level.addAlwaysVisibleParticle(
				type,
				position.x,
				position.y,
				position.z,
				velocity.x,
				velocity.y,
				velocity.z
		);
	}

	private Vec3 burstVector(Vec3 nail) {
		return safeDirection(target().subtract(nail));
	}

	private Vec3 safeDirection(Vec3 vector) {
		if (vector.lengthSqr() < 1.0E-5) {
			return randomVelocity(1.0);
		}
		return vector.normalize();
	}

	private Vec3 jitter(Vec3 position, double radius) {
		return position.add(
				(random.nextDouble() - 0.5) * radius,
				(random.nextDouble() - 0.5) * radius,
				(random.nextDouble() - 0.5) * radius
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
