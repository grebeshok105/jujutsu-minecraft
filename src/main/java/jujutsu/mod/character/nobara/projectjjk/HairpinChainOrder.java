package jujutsu.mod.character.nobara.projectjjk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;

public final class HairpinChainOrder {
	private HairpinChainOrder() {}

	public static List<Candidate> nearestNeighbor(Vec3 start, List<Candidate> input) {
		Vec3 cursor = Objects.requireNonNull(start, "start");
		List<Candidate> remaining = new ArrayList<>(input);
		List<Candidate> ordered = new ArrayList<>(input.size());
		while (!remaining.isEmpty()) {
			Vec3 from = cursor;
			Candidate next = remaining.stream().min(Comparator
					.comparingDouble((Candidate candidate) -> candidate.position().distanceToSqr(from))
					.thenComparing(Candidate::nailId)).orElseThrow();
			remaining.remove(next);
			ordered.add(next);
			cursor = next.position();
		}
		return List.copyOf(ordered);
	}

	public static List<Candidate> directed(UUID seedId, Vec3 seedPosition, List<Candidate> input) {
		Candidate seed = input.stream().filter(candidate -> candidate.nailId().equals(seedId)).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("directed seed missing"));
		List<Candidate> rest = input.stream().filter(candidate -> !candidate.nailId().equals(seedId)).toList();
		List<Candidate> result = new ArrayList<>(input.size());
		result.add(seed);
		result.addAll(nearestNeighbor(seedPosition, rest));
		return List.copyOf(result);
	}

	public record Candidate(UUID nailId, Vec3 position) {
		public Candidate {
			Objects.requireNonNull(nailId, "nailId");
			Objects.requireNonNull(position, "position");
		}
	}
}
