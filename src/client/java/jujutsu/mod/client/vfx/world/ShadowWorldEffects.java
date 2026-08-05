package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

public final class ShadowWorldEffects {
	private ShadowWorldEffects() {}

	public static void renderMegumiShadowPool(VertexConsumer consumer, Vec3 center, float progress, boolean opening) {
		float radius = shadowPoolRadius(opening, progress);
		int alpha = Math.round(255.0f * shadowPoolOpacity(opening, progress));
		renderPoolDisk(consumer, center, radius, alpha);
	}

	/**
	 * Shadow Trap pool, unfurling or collapsing at an absolute radius carried by the cue intensity
	 * (tenths of a block: intensity 26 is the 2.6-block trap zone).
	 */
	public static void renderShadowTrapPool(VertexConsumer consumer, Vec3 center, float progress, float radius, boolean opening) {
		renderPoolDisk(consumer, center, radius * shadowPoolRadius(opening, progress),
				Math.round(255.0f * shadowPoolOpacity(opening, progress)));
	}

	/**
	 * Liquid trap zone: a pool of constant radius with a gentle alpha breath, re-emitted by the
	 * server pulse rather than re-ticked by the client.
	 */
	public static void renderShadowTrapPool(VertexConsumer consumer, Vec3 center, float progress, float radius) {
		float clamped = Math.max(0.0f, Math.min(1.0f, progress));
		float alpha = 0.72f + 0.08f * (float) Math.sin(clamped * Math.PI * 2.0);
		renderPoolDisk(consumer, center, radius, Math.round(255.0f * alpha));
	}

	private static void renderPoolDisk(VertexConsumer consumer, Vec3 center, float radius, int alpha) {
		int segments = 24;
		for (int segment = 0; segment < segments; segment++) {
			double startAngle = segment * Math.PI * 2.0 / segments;
			double endAngle = (segment + 1) * Math.PI * 2.0 / segments;
			addVertex(consumer, center, alpha);
			addVertex(consumer, center, radius, startAngle, alpha);
			addVertex(consumer, center, radius, endAngle, alpha);
			addVertex(consumer, center, alpha);
		}
	}

	private static void addVertex(VertexConsumer consumer, Vec3 point, int alpha) {
		consumer.addVertex((float) point.x, (float) (point.y + 0.025), (float) point.z)
				.setColor(0, 0, 0, alpha);
	}

	private static void addVertex(VertexConsumer consumer, Vec3 center, float radius, double angle, int alpha) {
		consumer.addVertex(
				(float) (center.x + Math.cos(angle) * radius),
				(float) (center.y + 0.025),
				(float) (center.z + Math.sin(angle) * radius * 0.78))
				.setColor(0, 0, 0, alpha);
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
