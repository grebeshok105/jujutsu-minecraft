package jujutsu.mod.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;

public final class TargetResolver {
	private static final double ENTITY_SWEEP_RADIUS = 1.15;

	private TargetResolver() {}

	public enum Mode {
		ENTITY,
		BLOCK,
		MISS
	}

	public record BlockCandidate(Vec3 point, Vec3 normal) {}

	public record EntityCandidate(int entityId, Vec3 center, double radius) {}

	public record Result(Mode mode, Vec3 point, Vec3 normal, Optional<Integer> entityId, double maxRange) {}

	public static Result resolveForTests(Vec3 origin, Vec3 look, double maxRange, Optional<BlockCandidate> blockCandidate, List<EntityCandidate> entityCandidates, int ownerEntityId) {
		Vec3 direction = safeDirection(look);
		double blockDistance = blockCandidate
				.map(candidate -> Math.min(maxRange, Math.max(0.0, candidate.point().subtract(origin).dot(direction))))
				.orElse(maxRange);

		Optional<EntityCandidate> entity = entityCandidates.stream()
				.filter(candidate -> candidate.entityId() != ownerEntityId)
				.filter(candidate -> distanceAlongRay(origin, direction, candidate.center()) <= blockDistance)
				.filter(candidate -> distanceAlongRay(origin, direction, candidate.center()) <= maxRange)
				.filter(candidate -> perpendicularDistance(origin, direction, candidate.center()) <= candidate.radius() + ENTITY_SWEEP_RADIUS)
				.min(Comparator.comparingDouble(candidate -> distanceAlongRay(origin, direction, candidate.center())));

		if (entity.isPresent()) {
			EntityCandidate candidate = entity.get();
			Vec3 normal = safeDirection(candidate.center().subtract(origin)).scale(-1.0);
			return new Result(Mode.ENTITY, candidate.center(), normal, Optional.of(candidate.entityId()), maxRange);
		}

		if (blockCandidate.isPresent() && blockDistance <= maxRange) {
			BlockCandidate candidate = blockCandidate.get();
			return new Result(Mode.BLOCK, candidate.point(), safeDirection(candidate.normal()), Optional.empty(), maxRange);
		}

		return new Result(Mode.MISS, origin.add(direction.scale(maxRange)), direction.scale(-1.0), Optional.empty(), maxRange);
	}

	private static double distanceAlongRay(Vec3 origin, Vec3 direction, Vec3 point) {
		return Math.max(0.0, point.subtract(origin).dot(direction));
	}

	private static double perpendicularDistance(Vec3 origin, Vec3 direction, Vec3 point) {
		Vec3 offset = point.subtract(origin);
		Vec3 projected = direction.scale(Math.max(0.0, offset.dot(direction)));
		return offset.subtract(projected).length();
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}
}
