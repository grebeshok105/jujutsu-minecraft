package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

/**
 * Pure, side-neutral Hairpin network construction.
 *
 * <p>The chain is a deterministic Prim-like expansion: the seed starts the frontier and every next
 * node is the not-yet-selected candidate nearest to any node already in that frontier. Candidates
 * outside the radius of every frontier node form unreachable islands and are not included.
 */
public final class HairpinNetwork {
	private HairpinNetwork() {}

	public record Node(UUID nailId, Vec3 position, UUID targetId, int depth,
			NailAnchorRegistry.NailOrigin origin) {
		public Node {
			Objects.requireNonNull(nailId, "nailId");
			Objects.requireNonNull(position, "position");
			Objects.requireNonNull(origin, "origin");
			if (depth < 1) throw new IllegalArgumentException("depth must be positive");
		}
	}

	/**
	 * Builds one deterministic chain from {@code seed}.
	 *
	 * <p>All candidate snapshots are sorted by UUID before expansion. The sort removes collection
	 * iteration order from the result; UUID is also the final tie-break whenever two candidates have
	 * the same nearest-frontier distance.
	 */
	public static List<Node> build(Collection<Node> candidates, Node seed, double radius) {
		Objects.requireNonNull(candidates, "candidates");
		Objects.requireNonNull(seed, "seed");
		if (!Double.isFinite(radius) || radius < 0.0) {
			throw new IllegalArgumentException("radius must be finite and non-negative");
		}

		List<Node> sorted = new ArrayList<>(candidates);
		sorted.sort(Comparator.comparing(Node::nailId));
		Set<UUID> seenIds = new HashSet<>();
		List<Node> unique = new ArrayList<>(sorted.size());
		for (Node candidate : sorted) {
			if (!seenIds.add(candidate.nailId())) {
				throw new IllegalArgumentException("duplicate nailId: " + candidate.nailId());
			}
			unique.add(candidate);
		}

		List<Node> chain = new ArrayList<>(unique.size() + 1);
		List<Node> frontier = new ArrayList<>(unique.size() + 1);
		Set<UUID> selected = new HashSet<>();
		chain.add(seed);
		frontier.add(seed);
		selected.add(seed.nailId());
		double radiusSqr = radius * radius;

		while (true) {
			Node best = null;
			double bestDistanceSqr = Double.POSITIVE_INFINITY;
			for (Node candidate : unique) {
				if (selected.contains(candidate.nailId())) continue;
				double nearestDistanceSqr = nearestFrontierDistanceSqr(candidate, frontier);
				if (nearestDistanceSqr > radiusSqr) continue;
				if (best == null || nearestDistanceSqr < bestDistanceSqr
						|| (Double.compare(nearestDistanceSqr, bestDistanceSqr) == 0
								&& candidate.nailId().compareTo(best.nailId()) < 0)) {
					best = candidate;
					bestDistanceSqr = nearestDistanceSqr;
				}
			}
			if (best == null) break;
			selected.add(best.nailId());
			chain.add(best);
			frontier.add(best);
		}
		return List.copyOf(chain);
	}

	private static double nearestFrontierDistanceSqr(Node candidate, List<Node> frontier) {
		double nearest = Double.POSITIVE_INFINITY;
		for (Node frontierNode : frontier) {
			nearest = Math.min(nearest, candidate.position().distanceToSqr(frontierNode.position()));
		}
		return nearest;
	}
}
