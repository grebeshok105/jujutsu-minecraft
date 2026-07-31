package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

public final class ShadowWorldEffects {
	private ShadowWorldEffects() {}

	public static void renderMegumiShadowPool(VertexConsumer consumer, Vec3 center, float progress, boolean opening) {
		float radius = shadowPoolRadius(opening, progress);
		int alpha = Math.round(255.0f * shadowPoolOpacity(opening, progress));
		int segments = 20;
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
