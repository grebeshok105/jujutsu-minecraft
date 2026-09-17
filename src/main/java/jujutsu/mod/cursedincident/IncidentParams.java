package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;

/**
 * The rolled per-incident parameters (issue #110 spec §5): a template supplies the
 * structure, these supply the variance. Everything here derives from the incident seed
 * via {@code SpawnRollPolicy}, so identical seed + identical conditions replay the same
 * incident.
 *
 * @param zoneShape          geometry id understood by the infection layer ("sphere"/"column")
 * @param baseRadius         zone radius before stage scaling
 * @param curseWeights       spirit tier id -> spawn weight for this incident
 * @param atmosphereId       which atmosphere recipe family the client plays
 * @param localGoals         optional objective ids the template offers
 * @param escalationSpeedMul multiplies age progression speed (grade-driven)
 * @param ignoreShelter      whether zone spirits skip the day-shelter goal
 * @param secondaryAtCritical whether CRITICAL may mint a self-sustaining secondary node
 * @param dwellTicksRequired how long an unsealed object must dwell to move the centre
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
		curseWeights = Map.copyOf(curseWeights);
		localGoals = List.copyOf(localGoals);
	}
}
