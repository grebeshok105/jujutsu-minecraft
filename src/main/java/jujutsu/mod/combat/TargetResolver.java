package jujutsu.mod.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

	public static Result resolve(ServerLevel level, ServerPlayer owner, double maxRange) {
		return resolve(level, owner, maxRange, living -> true);
	}

	/** Resolves a server-side aimed living target while applying a character-specific eligibility predicate. */
	public static Result resolve(ServerLevel level, ServerPlayer owner, double maxRange, Predicate<LivingEntity> eligible) {
		Vec3 origin = owner.getEyePosition();
		Vec3 look = owner.getLookAngle();
		Vec3 direction = safeDirection(look);
		Vec3 end = origin.add(direction.scale(maxRange));
		BlockHitResult blockHit = level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
		Optional<BlockCandidate> blockCandidate = blockHit.getType() == HitResult.Type.MISS
				? Optional.empty()
				: Optional.of(new BlockCandidate(blockHit.getLocation(), directionVector(blockHit.getDirection())));
		AABB sweepBounds = new AABB(origin, end).inflate(2.25);
		List<EntityCandidate> entityCandidates = level.getEntities(owner, sweepBounds,
				entity -> entity instanceof LivingEntity living && living.isAlive() && eligible.test(living)).stream()
				.map(entity -> {
					AABB bounds = entity.getBoundingBox();
					double radius = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5;
					return new EntityCandidate(entity.getId(), bounds.getCenter(), radius);
				})
				.toList();
		return resolveForTests(origin, look, maxRange, blockCandidate, entityCandidates, owner.getId());
	}

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
				.min(Comparator
							.comparingDouble((EntityCandidate candidate) -> perpendicularDistance(origin, direction, candidate.center()))
							.thenComparingDouble(candidate -> distanceAlongRay(origin, direction, candidate.center())));

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

	private static Vec3 directionVector(Direction direction) {
		return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
	}
}
