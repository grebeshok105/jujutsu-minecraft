package jujutsu.mod.combat;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side aim resolution for directed combat actions.
 * Entities are picked by ray–AABB intersection (with a small aim assist inflate),
 * not by "center near ray" approximation — the latter fails when the look ray
 * hits the floor in front of a mob's bounding-box center.
 */
public final class TargetResolver {
	/** Soft aim-assist padding around living hitboxes (blocks). */
	private static final double ENTITY_HITBOX_INFLATE = 0.35;
	/** Extra search volume around the aim segment. */
	private static final double SEARCH_INFLATE = 2.5;

	private TargetResolver() {}

	public enum Mode {
		ENTITY,
		BLOCK,
		MISS
	}

	public record BlockCandidate(Vec3 point, Vec3 normal) {}

	/**
	 * @param entityId entity network id
	 * @param center bounding-box center (debug / VFX)
	 * @param radius half-extent used by pure tests
	 * @param hitDistance distance from eye origin to the ray–AABB hit along the segment
	 */
	public record EntityCandidate(int entityId, Vec3 center, double radius, double hitDistance) {
		public EntityCandidate(int entityId, Vec3 center, double radius) {
			this(entityId, center, radius, center.length());
		}
	}

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
		double blockDistance = blockCandidate
				.map(candidate -> Math.min(maxRange, origin.distanceTo(candidate.point())))
				.orElse(maxRange);

		AABB sweepBounds = new AABB(origin, end).inflate(SEARCH_INFLATE);
		List<EntityCandidate> entityCandidates = level.getEntities(owner, sweepBounds, entity -> isCandidate(entity, eligible)).stream()
				.map(entity -> toCandidate(entity, origin, end, blockDistance))
				.flatMap(Optional::stream)
				.toList();

		return resolveForTests(origin, look, maxRange, blockCandidate, entityCandidates, owner.getId());
	}

	public static Result resolveForTests(
			Vec3 origin,
			Vec3 look,
			double maxRange,
			Optional<BlockCandidate> blockCandidate,
			List<EntityCandidate> entityCandidates,
			int ownerEntityId
	) {
		Vec3 direction = safeDirection(look);
		double blockDistance = blockCandidate
				.map(candidate -> Math.min(maxRange, Math.max(0.0, origin.distanceTo(candidate.point()))))
				.orElse(maxRange);

		Optional<EntityCandidate> entity = entityCandidates.stream()
				.filter(candidate -> candidate.entityId() != ownerEntityId)
				.filter(candidate -> candidate.hitDistance() > 1.0E-3)
				.filter(candidate -> candidate.hitDistance() <= maxRange + 1.0E-3)
				// Must be reached before the first solid block along the aim segment.
				.filter(candidate -> candidate.hitDistance() <= blockDistance + 1.0E-3)
				.min(Comparator
						.comparingDouble(EntityCandidate::hitDistance)
						.thenComparingDouble(candidate -> perpendicularDistance(origin, direction, candidate.center())));

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

	private static boolean isCandidate(Entity entity, Predicate<LivingEntity> eligible) {
		return entity instanceof LivingEntity living && living.isAlive() && eligible.test(living);
	}

	private static Optional<EntityCandidate> toCandidate(Entity entity, Vec3 origin, Vec3 end, double blockDistance) {
		AABB box = entity.getBoundingBox().inflate(ENTITY_HITBOX_INFLATE);
		Optional<Vec3> hit = box.clip(origin, end);
		if (hit.isEmpty()) {
			return Optional.empty();
		}
		double hitDistance = origin.distanceTo(hit.get());
		if (hitDistance > blockDistance + 1.0E-3) {
			return Optional.empty();
		}
		AABB bounds = entity.getBoundingBox();
		double radius = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5;
		return Optional.of(new EntityCandidate(entity.getId(), bounds.getCenter(), radius, hitDistance));
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
