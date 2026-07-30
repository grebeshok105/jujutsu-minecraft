package jujutsu.mod.client.vfx.world;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

final class VfxWorldGeometry {
	static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
	static final Vec3 EAST = new Vec3(1.0, 0.0, 0.0);
	static final Vec3 NORTH = new Vec3(0.0, 0.0, -1.0);

	private VfxWorldGeometry() {}

	static void renderDirectionalRing(VertexConsumer consumer, Vec3 center, Vec3 right, Vec3 up,
			float radius, float depthScale, int alpha, float phase,
			int darkR, int darkG, int darkB, int edgeR, int edgeG, int edgeB) {
		if (alpha <= 0) {
			return;
		}
		int segments = 20;
		for (int segment = 0; segment < segments; segment++) {
			double a0 = phase + segment * Math.PI * 2.0 / segments;
			double a1 = phase + (segment + 0.7) * Math.PI * 2.0 / segments;
			Vec3 start = center.add(right.scale(Math.cos(a0) * radius)).add(up.scale(Math.sin(a0) * radius * depthScale));
			Vec3 end = center.add(right.scale(Math.cos(a1) * radius)).add(up.scale(Math.sin(a1) * radius * depthScale));
			Vec3 side = sideVector(end.subtract(start), start.add(end).scale(0.5), 0.028f);
			addRibbon(consumer, start, end, side.scale(2.4f), darkR, darkG, darkB, Math.round(alpha * 0.48f));
			addRibbon(consumer, start, end, side, edgeR, edgeG, edgeB, alpha);
		}
	}

	static Vec3[] directionalBasis(Vec3 forward) {
		if (forward.lengthSqr() < 1e-6) {
			return new Vec3[]{EAST, UP};
		}
		Vec3 up = Math.abs(forward.dot(UP)) > 0.98 ? NORTH : UP;
		Vec3 right = forward.cross(up).normalize();
		Vec3 realUp = right.cross(forward).normalize();
		return new Vec3[]{right, realUp};
	}

	static Vec3 sideVector(Vec3 direction, Vec3 cameraRelativeMidpoint, float width) {
		Vec3 line = direction.lengthSqr() < 1.0E-5 ? UP : direction.normalize();
		Vec3 view = cameraRelativeMidpoint.lengthSqr() < 1.0E-5 ? EAST : cameraRelativeMidpoint.normalize();
		Vec3 side = line.cross(view);
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(UP);
		}
		if (side.lengthSqr() < 1.0E-5) {
			side = line.cross(EAST);
		}
		return side.normalize().scale(width);
	}

	static void addRibbon(VertexConsumer consumer, Vec3 start, Vec3 end, Vec3 side, int red, int green, int blue, int alpha) {
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x + side.x), (float) (start.y + side.y), (float) (start.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x + side.x), (float) (end.y + side.y), (float) (end.z + side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (end.x - side.x), (float) (end.y - side.y), (float) (end.z - side.z)).setColor(red, green, blue, alpha);
		consumer.addVertex((float) (start.x - side.x), (float) (start.y - side.y), (float) (start.z - side.z)).setColor(red, green, blue, alpha);
	}
}
