package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Resolves the same directed Hairpin seed on server and client snapshots. */
public final class HairpinSeedResolver {
	private static final double AIM_RANGE = 20.0;
	private static final double AIM_RAY_TOLERANCE = 1.0;
	private static final double BLOCK_ANCHOR_HIT_RADIUS = 1.5;

	private HairpinSeedResolver() {}

	/**
	 * Applies the directed seed precedence: owned anchor on the aimed entity, nearest anchor on the
	 * aim ray, then the nearest owned anchor at the aimed block. The candidate collection is already
	 * the caller's ownership-filtered snapshot; this method never scans or mutates world state.
	 */
	public static UUID resolveSeed(Level level, Vec3 eyePos, Vec3 lookVec, Entity aimedEntity,
			BlockHitResult blockHit, Collection<HairpinNetwork.Node> candidates) {
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(eyePos, "eyePos");
		Objects.requireNonNull(lookVec, "lookVec");
		Objects.requireNonNull(candidates, "candidates");
		List<HairpinNetwork.Node> snapshot = new ArrayList<>(candidates);
		snapshot.sort(Comparator.comparing(HairpinNetwork.Node::nailId));

		if (aimedEntity != null) {
			return snapshot.stream()
					.filter(node -> aimedEntity.getUUID().equals(node.targetId()))
					.min(Comparator.comparing(HairpinNetwork.Node::nailId))
					.map(HairpinNetwork.Node::nailId)
					.orElseGet(() -> resolveRay(eyePos, lookVec, snapshot, blockHit));
		}
		return resolveRay(eyePos, lookVec, snapshot, blockHit);
	}

	private static UUID resolveRay(Vec3 eyePos, Vec3 lookVec, List<HairpinNetwork.Node> candidates,
			BlockHitResult blockHit) {
		Vec3 direction = safeDirection(lookVec);
		HairpinNetwork.Node rayNode = candidates.stream()
				.map(node -> projection(eyePos, direction, node))
				.filter(candidate -> candidate.along() >= 0.0 && candidate.along() <= AIM_RANGE
						&& candidate.perpendicularSqr() <= AIM_RAY_TOLERANCE * AIM_RAY_TOLERANCE)
				.min(Comparator.comparingDouble(RayCandidate::along)
						.thenComparing(candidate -> candidate.node().nailId()))
				.map(RayCandidate::node)
				.orElse(null);
		if (rayNode != null) return rayNode.nailId();

		if (blockHit == null) return null;
		Vec3 hit = blockHit.getLocation();
		return candidates.stream()
				.filter(node -> node.position().distanceToSqr(hit) <= BLOCK_ANCHOR_HIT_RADIUS * BLOCK_ANCHOR_HIT_RADIUS)
				.min(Comparator.comparingDouble((HairpinNetwork.Node node) -> node.position().distanceToSqr(hit))
						.thenComparing(HairpinNetwork.Node::nailId))
				.map(HairpinNetwork.Node::nailId)
				.orElse(null);
	}

	private static RayCandidate projection(Vec3 origin, Vec3 direction, HairpinNetwork.Node node) {
		Vec3 offset = node.position().subtract(origin);
		double along = offset.dot(direction);
		double perpendicularSqr = offset.subtract(direction.scale(along)).lengthSqr();
		return new RayCandidate(node, along, perpendicularSqr);
	}

	private static Vec3 safeDirection(Vec3 vector) {
		return vector.lengthSqr() < 1.0E-8 ? new Vec3(0.0, 0.0, 1.0) : vector.normalize();
	}

	private record RayCandidate(HairpinNetwork.Node node, double along, double perpendicularSqr) {}
}
