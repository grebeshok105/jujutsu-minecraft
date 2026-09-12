package jujutsu.mod.character.megumi;

import net.minecraft.world.phys.Vec3;

/** Pure Max Elephant numbers: the trunk water-jet corridor and its shove. */
public final class MegumiElephantPolicy {
	private MegumiElephantPolicy() {}

	/**
	 * Whether {@code point} sits inside the jet corridor: longitudinal distance along
	 * {@code direction} within {@code [0, length]}, lateral distance at most {@code halfWidth}.
	 * Points behind the trunk fail; a degenerate (zero) direction contains nothing.
	 */
	public static boolean inJetCorridor(Vec3 point, Vec3 origin, Vec3 direction, double length, double halfWidth) {
		Vec3 unit = safeUnit(direction);
		if (unit == null) {
			return false;
		}
		Vec3 relative = point.subtract(origin);
		double longitudinal = relative.dot(unit);
		if (longitudinal < 0.0 || longitudinal > length) {
			return false;
		}
		return relative.subtract(unit.scale(longitudinal)).length() <= halfWidth;
	}

	/**
	 * Horizontal shove direction of the jet: the jet direction flattened to the ground plane and
	 * normalized. Degenerate (zero or purely vertical) directions shove nowhere. The caller passes
	 * the components negated into {@code LivingEntity.knockback}, per the repo idiom.
	 */
	public static Vec3 knockbackVector(Vec3 direction) {
		Vec3 flat = new Vec3(direction.x, 0.0, direction.z);
		if (flat.lengthSqr() < 1.0E-8) {
			return Vec3.ZERO;
		}
		return flat.normalize();
	}

	private static Vec3 safeUnit(Vec3 direction) {
		if (direction.lengthSqr() < 1.0E-8) {
			return null;
		}
		return direction.normalize();
	}
}
