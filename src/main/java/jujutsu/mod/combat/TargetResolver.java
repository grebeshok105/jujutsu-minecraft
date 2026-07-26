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
 *
 * <p>Detection and ranking are separate concerns. Detection stays ray–AABB; ranking prefers a body the
 * ray truly entered, and only sorts the aim-assist grazes by crosshair angle. Every ordering key is
 * followed by the entity id, so a tie can never be decided by entity iteration order.
 *
 * <p>Shared by Todo's swap and three Nobara paths, so any change here is a roster-wide gameplay change.
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
	 * @param pierced whether the aim ray entered the real hitbox rather than only the aim-assist pad
	 */
	public record EntityCandidate(int entityId, Vec3 center, double radius, double hitDistance, boolean pierced) {
		/** Aim assist is the exception, so a candidate counts as a real hit unless it says otherwise. */
		public EntityCandidate(int entityId, Vec3 center, double radius, double hitDistance) {
			this(entityId, center, radius, hitDistance, true);
		}

		public EntityCandidate(int entityId, Vec3 center, double radius) {
			this(entityId, center, radius, center.length(), true);
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
						// A body the ray actually entered always beats one only the aim-assist pad caught,
						// so assist can never steal the target from what the player is looking straight at.
						.comparing((EntityCandidate candidate) -> !candidate.pierced())
						// Real hits rank by depth along the ray. Assist-only grazes rank by how far off the
						// crosshair they sit — that is the "closest to the crosshair" rule, and it is the key
						// that used to be unreachable: it sat behind an exact-equality test on hitDistance,
						// which two real entities essentially never satisfy.
						.thenComparingDouble(candidate -> candidate.pierced()
								? candidate.hitDistance()
								: angularOffset(origin, direction, candidate.center()))
						.thenComparingDouble(EntityCandidate::hitDistance)
						// Final key: without it a perfectly tied pair is decided by entity-section iteration
						// order, which changes as entities move, so the target could flip between ticks.
						.thenComparingInt(EntityCandidate::entityId));

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
		AABB bounds = entity.getBoundingBox();
		Optional<Vec3> hit = bounds.inflate(ENTITY_HITBOX_INFLATE).clip(origin, end);
		if (hit.isEmpty()) {
			return Optional.empty();
		}
		double hitDistance = origin.distanceTo(hit.get());
		if (hitDistance > blockDistance + 1.0E-3) {
			return Optional.empty();
		}
		double radius = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize())) * 0.5;
		// The second clip is against the real box, so ranking can tell a genuine hit from assist padding.
		boolean pierced = bounds.clip(origin, end).isPresent();
		return Optional.of(new EntityCandidate(entity.getId(), bounds.getCenter(), radius, hitDistance, pierced));
	}

	/**
	 * Sine of the angle between the aim direction and the candidate, which is monotonic in that angle
	 * over the only range candidates can occupy — they all lie ahead of the origin along the segment.
	 * Distance-normalized on purpose: a raw perpendicular distance would punish far targets for being far.
	 */
	private static double angularOffset(Vec3 origin, Vec3 direction, Vec3 point) {
		Vec3 offset = point.subtract(origin);
		double length = offset.length();
		if (length < 1.0E-5) {
			return 0.0;
		}
		Vec3 projected = direction.scale(Math.max(0.0, offset.dot(direction)));
		return offset.subtract(projected).length() / length;
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-5 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private static Vec3 directionVector(Direction direction) {
		return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
	}
}
