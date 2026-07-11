package jujutsu.mod.character.nobara.projectjjk;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure, server-owned lifecycle and geometry for one triangular nail trap. */
public final class NailTrap {
	private static final double EDGE_EPSILON = 1.0E-7;

	private final UUID ownerId;
	private final String dimensionId;
	private final Point center;
	private final List<Point> vertices;
	private final List<UUID> nailIds;
	private final int lifetimeTicks;
	private final int collapseTicks;
	private int activeTicks;
	private UUID targetId;

	public NailTrap(UUID ownerId, String dimensionId, Point center, List<Point> vertices,
			List<UUID> nailIds, int lifetimeTicks, int collapseTicks) {
		if (ownerId == null || dimensionId == null || center == null) throw new IllegalArgumentException("trap identity is required");
		if (vertices == null || vertices.size() != ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT
				|| nailIds == null || nailIds.size() != ProjectJjkNobaraProfile.NAIL_TRAP_NAIL_COUNT) {
			throw new IllegalArgumentException("a nail trap requires exactly three vertices and nails");
		}
		this.ownerId = ownerId;
		this.dimensionId = dimensionId;
		this.center = center;
		this.vertices = List.copyOf(vertices);
		this.nailIds = List.copyOf(nailIds);
		this.lifetimeTicks = Math.max(1, lifetimeTicks);
		this.collapseTicks = Math.max(1, collapseTicks);
	}

	public UUID ownerId() { return ownerId; }
	public String dimensionId() { return dimensionId; }
	public Point center() { return center; }
	public List<Point> vertices() { return vertices; }
	public List<UUID> nailIds() { return nailIds; }
	public Optional<UUID> targetId() { return Optional.ofNullable(targetId); }
	public int activeTicks() { return activeTicks; }

	public void tick(boolean available) {
		if (available && targetId == null && !expired()) activeTicks++;
	}

	public boolean expired() { return activeTicks >= lifetimeTicks; }

	public boolean trigger(UUID targetId) {
		if (this.targetId != null || targetId == null || expired()) return false;
		this.targetId = targetId;
		return true;
	}

	public int collapseBeat(int elapsedTicks) {
		if (elapsedTicks < 0 || elapsedTicks >= collapseTicks) return -1;
		for (int index = 0; index < 3; index++) {
			if (elapsedTicks == index * collapseTicks / 3) return index;
		}
		return -1;
	}

	public boolean impactDue(int elapsedTicks) { return elapsedTicks >= collapseTicks; }

	public boolean contains(double x, double y, double z) {
		if (y < center.y() - 0.5 || y > center.y() + ProjectJjkNobaraProfile.NAIL_TRAP_PRISM_HEIGHT) return false;
		Point a = vertices.get(0);
		Point b = vertices.get(1);
		Point c = vertices.get(2);
		double d1 = sign(x, z, a, b);
		double d2 = sign(x, z, b, c);
		double d3 = sign(x, z, c, a);
		boolean negative = d1 < -EDGE_EPSILON || d2 < -EDGE_EPSILON || d3 < -EDGE_EPSILON;
		boolean positive = d1 > EDGE_EPSILON || d2 > EDGE_EPSILON || d3 > EDGE_EPSILON;
		return !(negative && positive);
	}

	public static Optional<UUID> selectTarget(List<TargetCandidate> candidates) {
		return candidates.stream().min(Comparator.comparingDouble(TargetCandidate::distanceSqr)
				.thenComparing(candidate -> candidate.targetId().toString()))
				.map(TargetCandidate::targetId);
	}

	private static double sign(double x, double z, Point a, Point b) {
		return (x - b.x()) * (a.z() - b.z()) - (a.x() - b.x()) * (z - b.z());
	}

	public record Point(double x, double y, double z) {}
	public record TargetCandidate(UUID targetId, double distanceSqr) {}

	/** Small ownership registry; replacing a trap returns the previous one for cleanup. */
	public static final class Registry {
		private final Map<UUID, NailTrap> traps = new HashMap<>();
		public Optional<NailTrap> replace(NailTrap trap) { return Optional.ofNullable(traps.put(trap.ownerId(), trap)); }
		public Optional<NailTrap> get(UUID ownerId) { return Optional.ofNullable(traps.get(ownerId)); }
		public void remove(UUID ownerId, NailTrap expected) { traps.remove(ownerId, expected); }
		public Iterable<NailTrap> values() { return List.copyOf(traps.values()); }
		public void clear() { traps.clear(); }
	}
}
