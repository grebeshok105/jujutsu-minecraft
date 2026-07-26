package jujutsu.mod.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class VfxSoundChannel {
	/**
	 * The two categories the swap speaks through and the one a menu needs. Everything else steps back for
	 * the duration. PLAYERS carries the clap, the whoosh and the landing report, so ducking it would mute
	 * the very thing the silence is making room for.
	 */
	private static final SoundSource[] KEPT_AUDIBLE = {SoundSource.PLAYERS, SoundSource.UI};

	private VfxSoundDuck.State duckState = VfxSoundDuck.State.IDLE;
	private long duckDeadlineMillis;

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

	/** Steps the world's own noise back for a beat. Extends an existing duck rather than restarting it. */
	public void duck(Minecraft client, int durationTicks, float initialAgeTicks) {
		long now = System.currentTimeMillis();
		long candidate = VfxSoundDuck.deadlineMillis(now, durationTicks, initialAgeTicks);
		if (duckState == VfxSoundDuck.State.DUCKED_BY_TODO) {
			duckDeadlineMillis = VfxSoundDuck.extendedDeadline(duckDeadlineMillis, candidate);
			return;
		}
		if (!VfxSoundDuck.canStart(duckState, client.screen != null, client.level != null) || candidate <= now) {
			return;
		}
		duckDeadlineMillis = candidate;
		duckState = VfxSoundDuck.State.DUCKED_BY_TODO;
		client.getSoundManager().pauseAllExcept(KEPT_AUDIBLE);
	}

	/** Idempotent, and a no-op from IDLE: a pause this channel did not start is not ours to lift. */
	public void restoreDuck(Minecraft client) {
		if (duckState != VfxSoundDuck.State.DUCKED_BY_TODO) {
			return;
		}
		duckState = VfxSoundDuck.State.IDLE;
		duckDeadlineMillis = 0L;
		client.getSoundManager().resume();
	}

	void tick(Minecraft client) {
		if (VfxSoundDuck.shouldRestore(duckState, System.currentTimeMillis(), duckDeadlineMillis, client.screen != null)) {
			restoreDuck(client);
		}
	}

	void clear() {
		// Disconnect, level change and a null level all arrive here, which is why the duck needs no cleanup
		// wiring of its own.
		restoreDuck(Minecraft.getInstance());
	}
}
