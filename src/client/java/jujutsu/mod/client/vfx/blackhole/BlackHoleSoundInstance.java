package jujutsu.mod.client.vfx.blackhole;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Marker subclass for every sound the black hole itself produces. The audio-duck mixin exempts
 * exactly this type, so the hole keeps speaking while the rest of the world is suppressed.
 */
public final class BlackHoleSoundInstance extends SimpleSoundInstance {
	public BlackHoleSoundInstance(ResourceLocation sound, SoundSource source, float volume, float pitch,
			RandomSource random, boolean looping, int delay, Attenuation attenuation,
			double x, double y, double z) {
		super(sound, source, volume, pitch, random, looping, delay, attenuation, x, y, z, false);
	}

	/** Positional variant anchored at the hole. */
	public static BlackHoleSoundInstance at(ResourceLocation sound, float volume, float pitch, RandomSource random,
			boolean looping, net.minecraft.world.phys.Vec3 pos) {
		return new BlackHoleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, random, looping, 0,
				SoundInstance.Attenuation.LINEAR, pos.x, pos.y, pos.z);
	}

}
