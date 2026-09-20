package jujutsu.mod.client.render.cursedspirit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Render-only interpolation for GRIPPED victims that are pinned server-side (the Toad's
 * hold). Mounted runner victims are excluded (issue #119): their seat is already smooth on
 * both sides, so this store only eases pinned bodies toward the latest replicated position
 * over approximately three ticks, keeping repeated authoritative teleports from becoming
 * visible frame-to-frame jumps. Release deliberately keeps the current visual point for the
 * hand-back transition before the renderer returns to vanilla entity coordinates.
 */
public final class CarriedVictimSmoothing {
	public static final double INTERPOLATION_TICKS = 3.0;
	/** Ticks without a render call before a sample is presumed orphaned (entity unloaded). */
	private static final long STALE_AFTER_TICKS = 100L;
	private static final Map<UUID, Sample> SAMPLES = new HashMap<>();

	private CarriedVictimSmoothing() {
	}

	/** Returns the render position for a living entity, keyed only by its GRIPPED marker. */
	public static Vec3 position(LivingEntity victim, float partialTick) {
		if (victim == null) {
			return Vec3.ZERO;
		}
		Vec3 serverPosition = victim.position();
		long gameTime = victim.level().getGameTime();
		if (victim.hasEffect(JujutsuEffects.GRIPPED)) {
			Sample sample = SAMPLES.computeIfAbsent(victim.getUUID(), id ->
					new Sample(serverPosition, serverPosition, gameTime));
			advance(sample, serverPosition, gameTime);
			return sample.visual.lerp(sample.target, frameFactor(partialTick));
		}
		Sample sample = SAMPLES.get(victim.getUUID());
		if (sample == null) {
			return serverPosition;
		}
		advance(sample, serverPosition, gameTime);
		Vec3 rendered = sample.visual.lerp(sample.target, frameFactor(partialTick));
		if (rendered.distanceToSqr(serverPosition) < 1.0E-5) {
			SAMPLES.remove(victim.getUUID());
		}
		return rendered;
	}

	/**
	 * True only while this entity needs a render-position override — held, or still easing
	 * back after release. The mixin must ask before writing state.x/y/z: writing the raw
	 * {@code position()} for every living entity would kill vanilla partial-tick
	 * interpolation globally.
	 *
	 * <p>Issue #119: a mounted runner victim is excluded — the passenger seat is already
	 * smooth on both sides, and easing the render position on top would lag the model
	 * behind the real hand attachment.
	 */
	public static boolean hasVisualOverride(LivingEntity victim) {
		return victim != null && !victim.isPassenger()
				&& (victim.hasEffect(JujutsuEffects.GRIPPED) || SAMPLES.containsKey(victim.getUUID()));
	}

	/**
	 * Evicts samples whose entity stopped rendering — an unloaded or despawned victim never
	 * reaches the convergence removal in {@link #position}, so without a TTL its UUID
	 * accumulates until the next disconnect or level change.
	 */
	public static void tick() {
		net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
		if (client.level == null) {
			return;
		}
		long now = client.level.getGameTime();
		SAMPLES.values().removeIf(sample -> now - sample.gameTime > STALE_AFTER_TICKS);
	}

	private static void advance(Sample sample, Vec3 serverPosition, long gameTime) {
		long elapsed = Math.max(0L, gameTime - sample.gameTime);
		if (elapsed > 0L) {
			double factor = Math.min(1.0, elapsed / INTERPOLATION_TICKS);
			sample.visual = sample.visual.lerp(sample.target, factor);
			sample.gameTime = gameTime;
		}
		sample.target = serverPosition;
	}

	private static double frameFactor(float partialTick) {
		return Math.max(0.0, Math.min(1.0, partialTick / INTERPOLATION_TICKS));
	}

	/** Clears client state on disconnect/level teardown. */
	public static void clear() {
		SAMPLES.clear();
	}

	/** Test-visible count for ensuring release does not leak UUIDs. */
	public static int trackedCount() {
		return SAMPLES.size();
	}

	private static final class Sample {
		private Vec3 visual;
		private Vec3 target;
		private long gameTime;

		private Sample(Vec3 visual, Vec3 target, long gameTime) {
			this.visual = visual;
			this.target = target;
			this.gameTime = gameTime;
		}
	}
}
