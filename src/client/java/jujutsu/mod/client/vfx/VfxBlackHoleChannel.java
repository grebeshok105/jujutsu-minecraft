package jujutsu.mod.client.vfx;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.vfx.blackhole.BlackHoleRenderer;
import jujutsu.mod.client.vfx.blackhole.BlackHoleSoundInstance;
import jujutsu.mod.client.vfx.blackhole.BlackHoleState;
import jujutsu.mod.client.vfx.blackhole.BlackHoleTiming;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.vfx.VfxCue;

/**
 * The black hole's single slot: at most one hole exists at a time, and a trigger while one is
 * alive is refused (the spec's "command does nothing" rule).
 *
 * <p>Same split as {@code VfxDomainSphereChannel}: state only, no events, no GL in the
 * constructor — wiring lives in {@link VfxDirector}, GPU work in {@link BlackHoleRenderer}, and
 * the sound lifecycle in {@link #tickSounds}. The duck amount other systems read comes from
 * {@link #duckAmount()}, which the SoundEngine mixin multiplies into every non-black-hole sound.
 */
public final class VfxBlackHoleChannel implements AutoCloseable {
	private final BlackHoleRenderer renderer = new BlackHoleRenderer();
	private BlackHoleState active;
	private boolean disabledForSession;
	private boolean droneStarted;
	private boolean impulsePlayed;

	/**
	 * Attempts to start a hole. Returns {@code false} when one is already alive — the caller turns
	 * that into the "already exists" diagnostic instead of spawning a second one.
	 */
	public boolean tryTrigger(VfxCue cue, BlackHoleTiming timing, Vec3 diskNormal, float diskPhase) {
		if (disabledForSession || active != null) {
			return false;
		}
		Vec3 origin = cue.origin();
		if (!Double.isFinite(origin.x) || !Double.isFinite(origin.y) || !Double.isFinite(origin.z)) {
			JujutsuMod.LOGGER.warn("Dropping black hole cue with non-finite origin {}", origin);
			return false;
		}
		active = new BlackHoleState(origin, timing, cue.startGameTime(), diskNormal, diskPhase);
		droneStarted = false;
		impulsePlayed = false;
		return true;
	}

	public BlackHoleState active() {
		return active;
	}

	public boolean hasActive() {
		return active != null;
	}

	/** Force-removes the hole (debug tooling). Sounds are stopped, state dropped. */
	public void forceRemove(Minecraft client) {
		stopSounds(client);
		active = null;
	}

	/** World-depth snapshot: called at WorldRenderEvents.LAST while a hole is alive. */
	void captureWorldDepth(Minecraft client) {
		if (active != null) {
			renderer.captureWorldDepth(client);
		}
	}

	/** Post-hand composite: called from the tail of GameRenderer.renderLevel. */
	public void render(Minecraft client, WorldRenderContext context) {
		if (disabledForSession || active == null) {
			return;
		}
		float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
		long gameTime = context.world().getGameTime();
		float age = active.ageTicks(gameTime, partialTick);
		if (active.isExpired(gameTime, partialTick)) {
			finish(client);
			return;
		}
		renderer.render(client, context, active, age);
	}

	/**
	 * Per-tick sound driving: prelude rumble at trigger, the drone stack at the appearance, the
	 * disappearance impulse at the jolt, and hard stops at expiry. Runs on the client tick so it
	 * works even while the player is not looking at the hole.
	 */
	void tickSounds(Minecraft client) {
		if (active == null || client.level == null) {
			return;
		}
		long gameTime = client.level.getGameTime();
		float age = active.ageTicks(gameTime, 0.0f);
		BlackHoleTiming timing = active.timing();

		if (!droneStarted && age >= timing.appearStart()) {
			droneStarted = true;
			Vec3 pos = active.centerWorld();
			var random = client.level.getRandom();
			// The drone is strictly positional: it emanates from the hole's centre, never from
			// "inside the head" — the spec's pressure comes from loudness, not a floating layer.
			SoundInstance positional = BlackHoleSoundInstance.at(
					JujutsuSounds.BLACK_HOLE_DRONE.location(), 1.4f, 1.0f, random, true, pos);
			active.positionalSound(positional);
			client.getSoundManager().play(positional);
		}
		if (!impulsePlayed && age >= timing.disappearStart()) {
			impulsePlayed = true;
			client.getSoundManager().play(BlackHoleSoundInstance.at(
					JujutsuSounds.BLACK_HOLE_IMPULSE.location(), 1.0f, 1.0f,
					client.level.getRandom(), false, active.centerWorld()));
		}
		if (age >= timing.aftermathStart()) {
			stopSounds(client);
		}
		if (active.isExpired(gameTime, 0.0f)) {
			finish(client);
		}
	}

	/** Prelude rumble: fired once at trigger time by the recipe starter. */
	public void playPrelude(Minecraft client, Vec3 origin) {
		if (client.level == null) {
			return;
		}
		client.getSoundManager().play(BlackHoleSoundInstance.at(
				JujutsuSounds.BLACK_HOLE_PRELUDE.location(), 1.0f, 1.0f,
				client.level.getRandom(), false, origin));
	}

	/**
	 * Current world-audio suppression, 0..1. The SoundEngine mixin reads this every volume
	 * calculation; 0 means vanilla behaviour.
	 */
	public float duckAmount() {
		if (active == null) {
			return 0.0f;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			return 0.0f;
		}
		float age = active.ageTicks(client.level.getGameTime(), 0.0f);
		return active.timing().duckAmount(age);
	}

	/** HUD warp strength for the GuiRenderer mixin; 0 disables the redirect. */
	public float hudWarpActive() {
		if (active == null) {
			return 0.0f;
		}
		Minecraft client = Minecraft.getInstance();
		if (client == null || client.level == null) {
			return 0.0f;
		}
		float age = active.ageTicks(client.level.getGameTime(), 0.0f);
		return active.timing().intensity(age) + active.timing().jolt(age);
	}

	public BlackHoleRenderer renderer() {
		return renderer;
	}

	private void stopSounds(Minecraft client) {
		if (active == null) {
			return;
		}
		if (active.positionalSound() instanceof SoundInstance s) {
			client.getSoundManager().stop(s);
		}
		active.positionalSound(null);
	}

	private void finish(Minecraft client) {
		stopSounds(client);
		active = null;
	}

	void clear() {
		finish(Minecraft.getInstance());
	}

	void resetSession() {
		clear();
		disabledForSession = false;
		renderer.resetSession();
	}

	@Override
	public void close() {
		clear();
		renderer.close();
	}
}
