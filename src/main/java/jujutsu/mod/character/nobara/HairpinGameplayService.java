package jujutsu.mod.character.nobara;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class HairpinGameplayService {
	private HairpinGameplayService() {}

	public static float damageForNailCount(int nailCount) {
		return Math.min(64.0f, 32.0f + 8.0f * Math.max(0, nailCount));
	}

	public static float knockbackForNailCount(int nailCount) {
		return 1.4f + 0.25f * Math.max(0, nailCount);
	}

	public static List<Vec3> cinematicNailStarts(Vec3 origin, Vec3 look, Vec3 target, int nailCount) {
		Vec3 forward = safeDirection(look);
		Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
		if (right.lengthSqr() < 1.0E-5) {
			right = new Vec3(1.0, 0.0, 0.0);
		} else {
			right = right.normalize();
		}
		Vec3 up = right.cross(forward).normalize();
		double[][] offsets = {
				{-0.75, 0.46, -0.35},
				{0.75, 0.36, -0.3},
				{-0.35, -0.34, -0.2},
				{0.42, -0.45, -0.15}
		};
		List<Vec3> nails = new ArrayList<>();
		for (int index = 0; index < Math.min(4, Math.max(0, nailCount)); index++) {
			double[] offset = offsets[index];
			Vec3 start = origin
					.add(right.scale(offset[0]))
					.add(up.scale(offset[1]))
					.add(forward.scale(offset[2]));
			if (start.distanceToSqr(target) <= 16.0) {
				start = origin.subtract(forward.scale(1.5)).add(right.scale(offset[0])).add(up.scale(offset[1]));
			}
			nails.add(start);
		}
		return nails;
	}

	public static List<Vec3> preparedNailRow(Vec3 origin, Vec3 look, int nailCount) {
		Vec3 forward = safeDirection(look);
		Vec3 right = rightOf(forward);
		double[] offsets = {-0.72, -0.24, 0.24, 0.72};
		List<Vec3> nails = new ArrayList<>();
		for (int index = 0; index < Math.min(4, Math.max(0, nailCount)); index++) {
			nails.add(origin
					.add(forward.scale(1.15))
					.add(right.scale(offsets[index]))
					.add(0.0, -0.24, 0.0));
		}
		return nails;
	}

	private static Vec3 rightOf(Vec3 forward) {
		Vec3 right = new Vec3(forward.z, 0.0, -forward.x);
		if (right.lengthSqr() < 1.0E-5) {
			return new Vec3(1.0, 0.0, 0.0);
		}
		return right.normalize();
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
