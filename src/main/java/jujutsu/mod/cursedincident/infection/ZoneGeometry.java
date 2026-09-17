package jujutsu.mod.cursedincident.infection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import jujutsu.mod.cursedincident.IncidentParams;

/** Pure geometry operations shared by world infection and client-facing proximity logic. */
public final class ZoneGeometry {
	public enum Shape {
		SPHERE,
		COLUMN;

		public static Shape fromId(String id) {
			if (id == null) {
				return SPHERE;
			}
			try {
				return valueOf(id.trim().toUpperCase(java.util.Locale.ROOT));
			} catch (IllegalArgumentException ignored) {
				return SPHERE;
			}
		}
	}

	private ZoneGeometry() {
	}

	public static Shape shapeOf(IncidentParams params) {
		return params == null ? Shape.SPHERE : Shape.fromId(params.zoneShape());
	}

	/** Returns whether the block centre lies inside the zone. */
	public static boolean contains(Shape shape, BlockPos center, double radius, BlockPos pos) {
		if (center == null || pos == null || radius < 0.0) {
			return false;
		}
		double dx = pos.getX() - center.getX();
		double dy = pos.getY() - center.getY();
		double dz = pos.getZ() - center.getZ();
		double distanceSqr = shape == Shape.COLUMN ? dx * dx + dz * dz : dx * dx + dy * dy + dz * dz;
		return distanceSqr <= radius * radius + 1.0E-9;
	}

	/** Convenience overload for records whose shape is stored as a parameter id. */
	public static boolean contains(String shape, BlockPos center, double radius, BlockPos pos) {
		return contains(Shape.fromId(shape), center, radius, pos);
	}

	/**
	 * Samples unique block positions deterministically from the supplied random stream. Every returned
	 * position is in the requested shape; a bounded attempt count prevents pathological duplicate
	 * streams from hanging a server tick.
	 */
	public static List<BlockPos> sampleBlocks(Shape shape, BlockPos center, double radius,
			RandomSource random, int count) {
		if (center == null || random == null || count <= 0 || radius < 0.0) {
			return List.of();
		}
		Set<BlockPos> samples = new LinkedHashSet<>();
		if (radius == 0.0) {
			samples.add(center.immutable());
			return List.copyOf(samples);
		}
		int attempts = Math.max(32, count * 16);
		for (int attempt = 0; attempt < attempts && samples.size() < count; attempt++) {
			double x = (random.nextDouble() * 2.0 - 1.0) * radius;
			double z = (random.nextDouble() * 2.0 - 1.0) * radius;
			double y = shape == Shape.COLUMN
				? (random.nextDouble() * 2.0 - 1.0) * radius
				: (random.nextDouble() * 2.0 - 1.0) * radius;
			if (shape == Shape.SPHERE && x * x + y * y + z * z > radius * radius) {
				continue;
			}
			if (shape == Shape.COLUMN && x * x + z * z > radius * radius) {
				continue;
			}
			BlockPos sampled = BlockPos.containing(center.getX() + x, center.getY() + y, center.getZ() + z);
			if (!contains(shape, center, radius, sampled)) {
				continue;
			}
			samples.add(sampled.immutable());
		}
		// A small radius can contain fewer integer cells than the caller requested. Fill deterministically
		// by walking the local cube rather than returning duplicates or an out-of-zone position.
		if (samples.size() < count) {
			int bound = (int) Math.ceil(radius);
			for (int dx = -bound; dx <= bound && samples.size() < count; dx++) {
				for (int dy = -bound; dy <= bound && samples.size() < count; dy++) {
					for (int dz = -bound; dz <= bound && samples.size() < count; dz++) {
						BlockPos candidate = center.offset(dx, dy, dz);
						if (contains(shape, center, radius, candidate)) {
							samples.add(candidate.immutable());
						}
					}
				}
			}
		}
		return new ArrayList<>(samples);
	}

	/** String-shaped overload used by persisted incident parameters. */
	public static List<BlockPos> sampleBlocks(String shape, BlockPos center, double radius,
			RandomSource random, int count) {
		return sampleBlocks(Shape.fromId(shape), center, radius, random, count);
	}

	/** Positive distance from a point to the zone boundary; zero outside the zone. */
	public static double edgeDistance(Shape shape, BlockPos center, double radius, BlockPos pos) {
		if (!contains(shape, center, radius, pos)) {
			return 0.0;
		}
		double dx = pos.getX() - center.getX();
		double dy = pos.getY() - center.getY();
		double dz = pos.getZ() - center.getZ();
		double distance = shape == Shape.COLUMN ? Math.sqrt(dx * dx + dz * dz)
				: Math.sqrt(dx * dx + dy * dy + dz * dz);
		return Math.max(0.0, radius - distance);
	}

	public static double edgeDistance(String shape, BlockPos center, double radius, BlockPos pos) {
		return edgeDistance(Shape.fromId(shape), center, radius, pos);
	}

	/** Normalised centre-to-edge proximity used by atmosphere recipes. */
	public static double proximity(Shape shape, BlockPos center, double radius, BlockPos pos) {
		if (radius <= 0.0) {
			return contains(shape, center, radius, pos) ? 1.0 : 0.0;
		}
		return Math.max(0.0, Math.min(1.0, edgeDistance(shape, center, radius, pos) / radius));
	}
}
