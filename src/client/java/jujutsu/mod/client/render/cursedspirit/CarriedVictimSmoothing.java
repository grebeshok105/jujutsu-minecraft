package jujutsu.mod.client.render.cursedspirit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.registry.JujutsuEffects;

/**
 * Render-only interpolation for GRIPPED victims.
 *
 * <p>The server remains authoritative and continues to pin the entity. This store only keeps the
 * last visual point and eases it toward the latest replicated server position over approximately
 * three ticks, so repeated authoritative teleports do not become visible frame-to-frame jumps.
 * Release deliberately keeps the current visual point for the hand-back transition before the
 * renderer returns to vanilla entity coordinates.
 */
public final class CarriedVictimSmoothing {
	public static final double INTERPOLATION_TICKS = 3.0;
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
