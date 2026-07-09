package jujutsu.mod.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxParticleChannel {
	public void burst(Minecraft client, VfxQuality quality, ParticleOptions particle, Vec3 at, int count, double spread, double speed, RandomSource random) {
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		for (int index = 0; index < quality.scaledCount(count); index++) {
			level.addParticle(particle,
					at.x + (random.nextDouble() - 0.5) * spread * 2.0,
					at.y + (random.nextDouble() - 0.5) * spread * 2.0,
					at.z + (random.nextDouble() - 0.5) * spread * 2.0,
					(random.nextDouble() - 0.5) * speed,
					(random.nextDouble() - 0.5) * speed + speed * 0.5,
					(random.nextDouble() - 0.5) * speed);
		}
	}

	public void ring(Minecraft client, VfxQuality quality, ParticleOptions particle, Vec3 at, int count, double radius, double yOffset,
			double horizontalSpeed, RandomSource random) {
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		int scaledCount = quality.scaledCount(count);
		for (int index = 0; index < scaledCount; index++) {
			double angle = (index / (double) scaledCount) * Math.PI * 2.0 + random.nextDouble() * 0.08;
			double x = Math.cos(angle);
			double z = Math.sin(angle);
			level.addParticle(particle, at.x + x * radius, at.y + yOffset, at.z + z * radius, x * horizontalSpeed, 0.02, z * horizontalSpeed);
		}
	}

	void clear() {
	}
}
