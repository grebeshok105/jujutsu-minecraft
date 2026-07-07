package jujutsu.mod.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuParticles;

/**
 * Client-side particle bloom for the Straw Doll resonance / detonation, spawned directly into the
 * client level so the ritual reads as a cursed shockwave: a rising column of soul flame, cursed
 * stain motes, metal shards and a ground ring, scaled by the number of detonated marks.
 */
public final class ResonanceEffects {
	private static final RandomSource RANDOM = RandomSource.create();

	private ResonanceEffects() {}

	public static void spawnStrike(Vec3 at, int marks) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		int intensity = 26 + marks * 14;

		// Rising cursed column.
		for (int i = 0; i < intensity; i++) {
			double a = RANDOM.nextDouble() * Math.PI * 2.0;
			double r = RANDOM.nextDouble() * 0.5;
			double vx = Math.cos(a) * 0.04;
			double vz = Math.sin(a) * 0.04;
			double vy = 0.12 + RANDOM.nextDouble() * 0.22;
			level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
					at.x + Math.cos(a) * r, at.y + RANDOM.nextDouble() * 0.4, at.z + Math.sin(a) * r,
					vx, vy, vz);
		}
		// Cursed stain + spark cloud.
		burst(level, JujutsuParticles.HAIRPIN_MARK_STAIN, at, 6 + marks * 2, 0.5, 0.02);
		burst(level, JujutsuParticles.HAIRPIN_SPARK, at, intensity, 0.6, 0.24);
		burst(level, JujutsuParticles.HAIRPIN_BURST_METAL_SHARD, at, 8 + marks * 4, 0.6, 0.34);
		burst(level, ParticleTypes.CRIT, at, intensity, 0.6, 0.2);

		// Expanding ground ring of warn-edge marks.
		int ringCount = 18 + marks * 6;
		double radius = 1.2 + marks * 0.35;
		for (int i = 0; i < ringCount; i++) {
			double a = (i / (double) ringCount) * Math.PI * 2.0;
			double px = at.x + Math.cos(a) * radius;
			double pz = at.z + Math.sin(a) * radius;
			level.addParticle(JujutsuParticles.HAIRPIN_WARN_EDGE, px, at.y - 0.6, pz,
					Math.cos(a) * 0.06, 0.02, Math.sin(a) * 0.06);
		}
		level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0.0, 0.0, 0.0);
	}

	public static void spawnLinkBurst(Vec3 at) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		// A tight binding ring: cursed energy latches onto the target.
		int ringCount = 20;
		for (int i = 0; i < ringCount; i++) {
			double a = (i / (double) ringCount) * Math.PI * 2.0;
			double px = at.x + Math.cos(a) * 0.9;
			double pz = at.z + Math.sin(a) * 0.9;
			level.addParticle(JujutsuParticles.HAIRPIN_WARN_EDGE, px, at.y, pz,
					-Math.cos(a) * 0.05, 0.01, -Math.sin(a) * 0.05);
		}
		burst(level, JujutsuParticles.HAIRPIN_COMPRESSION_MOTE, at, 12, 0.5, 0.08);
	}

	private static void burst(ClientLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		for (int i = 0; i < count; i++) {
			level.addParticle(particle,
					at.x + (RANDOM.nextDouble() - 0.5) * spread * 2.0,
					at.y + (RANDOM.nextDouble() - 0.5) * spread * 2.0,
					at.z + (RANDOM.nextDouble() - 0.5) * spread * 2.0,
					(RANDOM.nextDouble() - 0.5) * speed,
					(RANDOM.nextDouble() - 0.5) * speed + speed * 0.5,
					(RANDOM.nextDouble() - 0.5) * speed);
		}
	}
}
