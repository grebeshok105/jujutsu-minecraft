package jujutsu.mod.character.megumi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/**
 * Spawn placement for the non-dog shikigami. Ground types reuse the dog slice's floor/hazard scan
 * ({@link MegumiGroundSafety}); the flyer needs a clearance check instead. The candidate builders
 * and the {@code Predicate} overloads are the testable seams — the level-backed methods only wire
 * them to the real world.
 */
public final class MegumiShikigamiSpawnPlacement {
	private MegumiShikigamiSpawnPlacement() {}

	/** First safe ground spot for a body near {@code around} (floor-supported, hazard-free, collision-free). */
	public static Vec3 ground(ServerLevel level, Vec3 around, float yRot, EntityDimensions dimensions) {
		for (Vec3 candidate : groundCandidates(around, yRot)) {
			Optional<Vec3> safe = MegumiGroundSafety.findVertical(level, candidate, dimensions);
			if (safe.isPresent()) {
				return safe.get();
			}
		}
		return null;
	}

	/** Hover spot {@code 2..4} blocks above {@code around}, requiring full clearance for the body box. */
	public static Vec3 air(ServerLevel level, Vec3 around, EntityDimensions dimensions) {
		return firstSafe(verticalCandidates(around), candidate -> {
			BlockPos pos = BlockPos.containing(candidate);
			return level.isLoaded(pos)
					&& level.getWorldBorder().isWithinBounds(pos)
					&& level.noBlockCollision(null, dimensions.makeBoundingBox(candidate));
		}).orElse(null);
	}

	/** Safe ground spots for {@code count} bodies on a ring around {@code center}; may return fewer. */
	public static List<Vec3> ring(ServerLevel level, Vec3 center, int count, double radius, EntityDimensions dimensions) {
		List<Vec3> spots = new ArrayList<>(count);
		for (Vec3 candidate : ringCandidates(center, count, radius)) {
			MegumiGroundSafety.findVertical(level, candidate, dimensions).ifPresent(spots::add);
		}
		return spots;
	}

	/** Side/forward candidates around the player, dog-summon shaped. */
	static List<Vec3> groundCandidates(Vec3 around, float yRot) {
		double yawRadians = Math.toRadians(yRot);
		Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		List<Vec3> candidates = new ArrayList<>(4);
		candidates.add(around.add(right.scale(1.5)));
		candidates.add(around.add(right.scale(-1.5)));
		candidates.add(around.add(forward.scale(1.8)));
		candidates.add(around);
		return candidates;
	}

	/** Hover candidates above the owner's head. */
	static List<Vec3> verticalCandidates(Vec3 around) {
		return List.of(around.add(0.0, 2.5, 0.0), around.add(0.0, 3.5, 0.0), around.add(0.0, 1.5, 0.0));
	}

	/** Evenly spaced ring points, angle 0 pointing +X. */
	static List<Vec3> ringCandidates(Vec3 center, int count, double radius) {
		List<Vec3> candidates = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			double angle = (2.0 * Math.PI * i) / count;
			candidates.add(center.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius));
		}
		return candidates;
	}

	static Optional<Vec3> firstSafe(List<Vec3> candidates, Predicate<Vec3> safe) {
		return candidates.stream().filter(safe).findFirst();
	}
}