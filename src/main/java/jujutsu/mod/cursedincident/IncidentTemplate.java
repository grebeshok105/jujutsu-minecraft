package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;

/**
 * One mechanical incident template (issue #110 spec §5): the structure of an event, not
 * its script. Concrete variance comes from {@link IncidentParams} rolled per incident.
 *
 * @param id             registry id ("blight", "nest", ...)
 * @param weight         selection weight in the template roll
 * @param baseRadius     default zone radius before grade/stage scaling
 * @param curseWeights   default spirit tier weights (params may re-roll)
 * @param atmospherePool atmosphere ids the param roll picks from
 * @param escalationMul  template-level escalation multiplier
 * @param allowSecondary whether this template may mint secondary nodes at all
 */
public record IncidentTemplate(
		String id,
		int weight,
		double baseRadius,
		Map<String, Integer> curseWeights,
		List<String> atmospherePool,
		double escalationMul,
		boolean allowSecondary) {

	public IncidentTemplate {
		curseWeights = Map.copyOf(curseWeights);
		atmospherePool = List.copyOf(atmospherePool);
	}
}
