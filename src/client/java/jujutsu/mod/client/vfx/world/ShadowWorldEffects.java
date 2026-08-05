package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

public final class ShadowWorldEffects {
	private ShadowWorldEffects() {}

	/** The dogs' decorative summon pool keeps its stylized depth squash. */
	private static final float SUMMON_POOL_Z_SCALE = 0.78f;

	public static void renderMegumiShadowPool(VertexConsumer consumer, Vec3 center, float progress, boolean opening) {
		float radius = shadowPoolRadius(opening, progress);
		int alpha = Math.round(255.0f * shadowPoolOpacity(opening, progress));
		renderPoolDisk(consumer, center, radius, alpha, SUMMON_POOL_Z_SCALE);
	}

	/**
	 * Shadow Trap pool, unfurling or collapsing at an absolute radius carried by the cue intensity
	 * (tenths of a block: intensity 26 is the 2.6-block trap zone). Trap pools are true circles:
	 * the drawn edge telegraphs the authoritative grip cylinder, so it must not lie on any axis,
	 * and the unfurl reaches the full radius so the open-to-zone handoff does not jump. Trap pools
	 * are void-black holes: full opacity for the pool's whole life, only the radius animates.
	 */
	public static void renderShadowTrapPool(VertexConsumer consumer, Vec3 center, float progress, float radius, boolean opening) {
		renderPoolDisk(consumer, center, radius * trapPoolScale(opening, progress), 255, 1.0f);
	}

	/**
	 * Liquid trap zone: a pool of constant radius and full opacity, re-emitted by the server pulse
	 * rather than re-ticked by the client. Reads as a hole in the world for its whole life.
	 */
	public static void renderShadowTrapPool(VertexConsumer consumer, Vec3 center, float progress, float radius) {
		renderPoolDisk(consumer, center, radius, 255, 1.0f);
	}

	private static void renderPoolDisk(VertexConsumer consumer, Vec3 center, float radius, int alpha, float zScale) {
		int segments = 24;
		for (int segment = 0; segment < segments; segment++) {
			double startAngle = segment * Math.PI * 2.0 / segments;
			double endAngle = (segment + 1) * Math.PI * 2.0 / segments;
			addVertex(consumer, center, alpha);
			addVertex(consumer, center, radius, startAngle, alpha, zScale);
			addVertex(consumer, center, radius, endAngle, alpha, zScale);
			addVertex(consumer, center, alpha);
		}
	}

	private static void addVertex(VertexConsumer consumer, Vec3 point, int alpha) {
		consumer.addVertex((float) point.x, (float) (point.y + 0.025), (float) point.z)
				.setColor(0, 0, 0, alpha);
	}

	private static void addVertex(VertexConsumer consumer, Vec3 center, float radius, double angle, int alpha, float zScale) {
		consumer.addVertex(
				(float) (center.x + Math.cos(angle) * radius),
				(float) (center.y + 0.025),
				(float) (center.z + Math.sin(angle) * radius * zScale))
				.setColor(0, 0, 0, alpha);
	}

	/** Trap unfurl/collapse sweep: 26% to the full authoritative radius, symmetric on close. */
	static float trapPoolScale(boolean opening, float progress) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		return opening ? 0.26f + 0.74f * clamped : 1.0f - 0.74f * clamped;
	}

	static float shadowPoolRadius(boolean opening, float progress) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		return opening ? 0.26f + 0.68f * clamped : 0.94f - 0.68f * clamped;
	}

	static float shadowPoolOpacity(boolean opening, float progress) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		return opening ? 0.88f + 0.08f * clamped : 0.96f * (1.0f - clamped);
	}
}
