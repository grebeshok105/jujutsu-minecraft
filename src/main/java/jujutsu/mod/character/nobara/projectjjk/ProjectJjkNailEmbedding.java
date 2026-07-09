package jujutsu.mod.character.nobara.projectjjk;

import net.minecraft.world.phys.Vec3;

public final class ProjectJjkNailEmbedding {
	private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

	private ProjectJjkNailEmbedding() {}

	static Vec3 bodyEmbedPoint(Vec3 targetPosition, double targetWidth, double targetHeight, Vec3 hitPoint, Vec3 direction, int seed) {
		Vec3 horizontalForward = new Vec3(direction.x, 0.0, direction.z);
		Vec3 forward = horizontalForward.lengthSqr() < 1.0E-5
				? safeDirection(new Vec3(hitPoint.x - targetPosition.x, 0.0, hitPoint.z - targetPosition.z))
				: safeDirection(horizontalForward);
		Vec3 right = forward.cross(UP);
		if (right.lengthSqr() < 1.0E-5) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		double surfaceRadius = Math.max(0.18, targetWidth * 0.42);
		double embedDepth = Math.min(0.3, Math.max(0.16, targetWidth * 0.34));
		double sideJitter = (((seed * 37) & 15) / 15.0 - 0.5) * targetWidth * 0.34;
		double heightJitter = (((seed * 53) & 15) / 15.0 - 0.5) * targetHeight * 0.22;
		double y = clamp(hitPoint.y - targetPosition.y, targetHeight * 0.32, targetHeight * 0.82) + heightJitter;
		y = clamp(y, targetHeight * 0.28, targetHeight * 0.86);
		return targetPosition
				.add(0.0, y, 0.0)
				.subtract(forward.scale(surfaceRadius))
				.add(forward.scale(embedDepth))
				.add(right.scale(sideJitter));
	}

	public static Vec3 worldOffset(Vec3 localOffset, float yawDegrees) {
		return rotateY(localOffset, yawDegrees);
	}

	public static Vec3 worldForward(Vec3 localForward, float yawDegrees) {
		return safeDirection(rotateY(localForward, yawDegrees));
	}

	private static Vec3 rotateY(Vec3 vector, float yawDegrees) {
		double radians = Math.toRadians(yawDegrees);
		double sin = Math.sin(radians);
		double cos = Math.cos(radians);
		return new Vec3(vector.x * cos - vector.z * sin, vector.y, vector.x * sin + vector.z * cos);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
