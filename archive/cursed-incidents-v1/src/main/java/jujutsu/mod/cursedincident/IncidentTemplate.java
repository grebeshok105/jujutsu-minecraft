package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;

/** A mechanical incident template: structure and weights, never a script. */
public record IncidentTemplate(
		String id,
		int weight,
		double baseRadius,
		Map<String, Integer> curseWeights,
		List<String> atmospherePool,
		double escalationMul,
		boolean allowSecondary) {

	public IncidentTemplate {
		id = id == null ? "" : id;
		weight = Math.max(0, weight);
		baseRadius = Double.isFinite(baseRadius) && baseRadius >= 0.0 ? baseRadius : 0.0;
		curseWeights = curseWeights == null ? Map.of() : Map.copyOf(curseWeights);
		atmospherePool = atmospherePool == null ? List.of() : List.copyOf(atmospherePool);
		escalationMul = Double.isFinite(escalationMul) && escalationMul > 0.0 ? escalationMul : 1.0;
	}
}
