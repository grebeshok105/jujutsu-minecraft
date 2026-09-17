package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;

/**
 * Seed-rolled parameters for one incident. A template describes structure; this
 * value carries the deterministic per-incident variation.
 */
public record IncidentParams(
		String zoneShape,
		double baseRadius,
		Map<String, Integer> curseWeights,
		String atmosphereId,
		List<String> localGoals,
		double escalationSpeedMul,
		boolean ignoreShelter,
		boolean secondaryAtCritical,
		long dwellTicksRequired) {

	public IncidentParams {
		zoneShape = zoneShape == null || zoneShape.isBlank() ? "sphere" : zoneShape;
		baseRadius = Double.isFinite(baseRadius) && baseRadius >= 0.0 ? baseRadius : 0.0;
		curseWeights = curseWeights == null ? Map.of() : Map.copyOf(curseWeights);
		atmosphereId = atmosphereId == null ? "" : atmosphereId;
		localGoals = localGoals == null ? List.of() : List.copyOf(localGoals);
		escalationSpeedMul = Double.isFinite(escalationSpeedMul) && escalationSpeedMul > 0.0
				? escalationSpeedMul : 1.0;
		dwellTicksRequired = Math.max(0L, dwellTicksRequired);
	}
}
