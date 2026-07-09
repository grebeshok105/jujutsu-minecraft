package jujutsu.mod.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxSoundChannel {
	public void playNoFalloff(Minecraft client, SoundEvent soundEvent, float volume, float pitch, Vec3 origin, RandomSource random) {
		client.getSoundManager().play(new SimpleSoundInstance(
				soundEvent.location(),
				SoundSource.PLAYERS,
				Math.max(0.0f, volume),
				pitch,
				random,
				false,
				0,
				SoundInstance.Attenuation.NONE,
				origin.x,
				origin.y,
				origin.z,
				false
		));
	}

	void clear() {
	}
}
