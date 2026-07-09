package jujutsu.mod.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.vfx.VfxCue;

public final class VfxContext {
	private final Minecraft client;
	private final VfxQuality quality;
	private final VfxWorldChannel world;
	private final VfxHudChannel hud;
	private final VfxCameraChannel camera;
	private final VfxFirstPersonChannel firstPerson;
	private final VfxParticleChannel particles;
	private final VfxSoundChannel sound;

	VfxContext(Minecraft client, VfxQuality quality, VfxWorldChannel world, VfxHudChannel hud, VfxCameraChannel camera,
			VfxFirstPersonChannel firstPerson, VfxParticleChannel particles, VfxSoundChannel sound) {
		this.client = client;
		this.quality = quality;
		this.world = world;
		this.hud = hud;
		this.camera = camera;
		this.firstPerson = firstPerson;
		this.particles = particles;
		this.sound = sound;
	}

	public Minecraft client() {
		return client;
	}

	public VfxQuality quality() {
		return quality;
	}

	public Vec3 resolveOrigin(VfxCue cue) {
		if (cue.anchorEntityId() == VfxCue.NO_ANCHOR || client.level == null) {
			return cue.origin();
		}
		Entity anchor = client.level.getEntity(cue.anchorEntityId());
		return anchor == null ? cue.origin() : anchor.position();
	}

	public float proximity(VfxCue cue, double radius) {
		if (client.player == null || radius <= 0.0) {
			return 0.0f;
		}
		double distance = client.player.position().distanceTo(resolveOrigin(cue));
		return (float) Math.max(0.0, Math.min(1.0, 1.0 - distance / radius));
	}

	public VfxWorldChannel world() {
		return world;
	}

	public VfxHudChannel hud() {
		return hud;
	}

	public VfxCameraChannel camera() {
		return camera;
	}

	public VfxFirstPersonChannel firstPerson() {
		return firstPerson;
	}

	public VfxParticleChannel particles() {
		return particles;
	}

	public VfxSoundChannel sound() {
		return sound;
	}

	public void burst(ParticleOptions particle, Vec3 at, int count, double spread, double speed, RandomSource random) {
		particles.burst(client, quality, particle, at, count, spread, speed, random);
	}

	public void ring(ParticleOptions particle, Vec3 at, int count, double radius, double yOffset, double horizontalSpeed, RandomSource random) {
		particles.ring(client, quality, particle, at, count, radius, yOffset, horizontalSpeed, random);
	}

	public void playNoFalloff(SoundEvent soundEvent, float volume, float pitch, Vec3 origin, RandomSource random) {
		sound.playNoFalloff(client, soundEvent, volume, pitch, origin, random);
	}
}
