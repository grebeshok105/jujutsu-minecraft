package jujutsu.mod.client.vfx.blackhole;

import net.minecraft.world.phys.Vec3;

/**
 * One live black hole: world anchor, lifecycle clock, disk orientation and the sound handles that
 * must die with it.
 *
 * <p>State only — no GL, no events, no Minecraft client lookups — so JUnit can drive the whole
 * lifecycle headless. The channel ({@code VfxBlackHoleChannel}) owns the singleton slot; the
 * renderer and the sound driver read this record each frame.
 */
public final class BlackHoleState {
	private final Vec3 centerWorld;
	private final BlackHoleTiming timing;
	private final long startGameTime;
	/** World-space disk normal: tilted off vertical so the ring reads as a ring, not a halo. */
	private final Vec3 diskNormal;
	/** Per-hole phase offset so two consecutive holes never share the same disk motion. */
	private final float diskPhase;

	private Object positionalSound;
	private Object innerSound;

	public BlackHoleState(Vec3 centerWorld, BlackHoleTiming timing, long startGameTime, Vec3 diskNormal, float diskPhase) {
		this.centerWorld = centerWorld;
		this.timing = timing;
		this.startGameTime = startGameTime;
		this.diskNormal = diskNormal.normalize();
		this.diskPhase = diskPhase;
	}

	public Vec3 centerWorld() {
		return centerWorld;
	}

	public BlackHoleTiming timing() {
		return timing;
	}

	public long startGameTime() {
		return startGameTime;
	}

	public Vec3 diskNormal() {
		return diskNormal;
	}

	public float diskPhase() {
		return diskPhase;
	}

	public float ageTicks(long gameTime, float partialTick) {
		return Math.max(0.0f, gameTime - startGameTime + partialTick);
	}

	public boolean isExpired(long gameTime, float partialTick) {
		return timing.isExpired(ageTicks(gameTime, partialTick));
	}

	/** Opaque sound handles (SoundInstance on the client); Object keeps this class Minecraft-free. */
	public Object positionalSound() {
		return positionalSound;
	}

	public void positionalSound(Object sound) {
		this.positionalSound = sound;
	}

	public Object innerSound() {
		return innerSound;
	}

	public void innerSound(Object sound) {
		this.innerSound = sound;
	}
}
