package jujutsu.mod.cursedincident.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jujutsu.mod.cursedincident.IncidentParams;
import jujutsu.mod.cursedincident.IncidentTemplate;
import jujutsu.mod.cursedincident.IncidentTemplates;
import jujutsu.mod.cursedincident.SourceKind;
import net.minecraft.util.RandomSource;

/** Seeded, world-free incident rolls. */
public final class SpawnRollPolicy {
	/** Pinned source split: 80% physical object, 20% free. */
	public static final double OBJECT_SOURCE_WEIGHT = 0.8;
	public static final long DEFAULT_DWELL_TICKS = 24_000L;

	private SpawnRollPolicy() {
	}

	public static SourceKind rollSourceKind(RandomSource random) {
		return uniform(random) < OBJECT_SOURCE_WEIGHT ? SourceKind.OBJECT : SourceKind.FREE;
	}

	/** Rolls a weighted template. A grade multiplier gently biases secondary-capable templates. */
	public static IncidentTemplate rollTemplate(
			RandomSource random, List<IncidentTemplate> templates, double gradeMul) {
		if (templates == null || templates.isEmpty()) {
			return null;
		}
		double multiplier = Double.isFinite(gradeMul) && gradeMul > 0.0 ? gradeMul : 1.0;
		double total = 0.0;
		for (IncidentTemplate template : templates) {
			if (template == null || template.weight() <= 0) {
				continue;
			}
			double bias = template.allowSecondary()
					? 1.0 + (multiplier - 1.0) * 0.35
					: 1.0 - (multiplier - 1.0) * 0.10;
			total += template.weight() * Math.max(0.10, bias);
		}
		if (total <= 0.0) {
			return templates.get(0);
		}
		double pick = uniform(random) * total;
		for (IncidentTemplate template : templates) {
			if (template == null || template.weight() <= 0) {
				continue;
			}
			double bias = template.allowSecondary()
					? 1.0 + (multiplier - 1.0) * 0.35
					: 1.0 - (multiplier - 1.0) * 0.10;
			pick -= template.weight() * Math.max(0.10, bias);
			if (pick < 0.0) {
				return template;
			}
		}
		return templates.get(templates.size() - 1);
	}

	public static IncidentTemplate rollTemplate(RandomSource random, double gradeMul) {
		return rollTemplate(random, IncidentTemplates.ALL, gradeMul);
	}

	/** Rolls all per-incident parameters from one deterministic random stream. */
	public static IncidentParams rollParams(RandomSource random, IncidentTemplate template, int grade) {
		if (template == null) {
			template = IncidentTemplates.BLIGHT;
		}
		int clampedGrade = TemplateRollPolicy.clampGrade(grade);
		double radiusVariance = 0.85 + random.nextDouble() * 0.30;
		double radius = template.baseRadius() * TemplateRollPolicy.maxRadiusMul(clampedGrade) * radiusVariance;
		Map<String, Integer> curseWeights = rollCurseSet(random, template, clampedGrade);
		String atmosphere = template.atmospherePool().isEmpty()
				? ""
				: template.atmospherePool().get(random.nextInt(template.atmospherePool().size()));
		List<String> goals = rollLocalGoals(random, template);
		boolean ignoreShelter = random.nextDouble() < 0.15;
		boolean secondary = template.allowSecondary() && random.nextDouble() < 0.60;
		return new IncidentParams(
				rollZoneShape(random),
				radius,
				curseWeights,
				atmosphere,
				goals,
				template.escalationMul() * TemplateRollPolicy.escalationSpeedMul(clampedGrade),
				ignoreShelter,
				secondary,
				DEFAULT_DWELL_TICKS);
	}

	/** Two geometry families are intentionally represented as stable wire strings. */
	public static String rollZoneShape(RandomSource random) {
		return random.nextBoolean() ? "sphere" : "column";
	}

	/** Returns a deterministic, grade-biased copy of a template curse set. */
	public static Map<String, Integer> rollCurseSet(
			RandomSource random, IncidentTemplate template, int grade) {
		Map<String, Integer> source = template == null ? Map.of() : template.curseWeights();
		if (source.isEmpty()) {
			return Map.of();
		}
		double bias = TemplateRollPolicy.curseTierBias(grade);
		Map<String, Integer> result = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> entry : source.entrySet()) {
			int base = Math.max(0, entry.getValue());
			double tierBias = switch (entry.getKey().toLowerCase()) {
				case "greater", "grade_1", "high" -> bias;
				case "common", "grade_2", "medium" -> 1.0 + (bias - 1.0) * 0.35;
				default -> 1.0;
			};
			int variance = random.nextInt(11) - 5;
			int rolled = (int) Math.round(base * tierBias) + variance;
			result.put(entry.getKey(), Math.max(0, rolled));
		}
		return Map.copyOf(result);
	}

	/** Convenience overload for callers that already carry a raw curse-weight table. */
	public static Map<String, Integer> rollCurseSet(
			RandomSource random, Map<String, Integer> curseWeights, int grade) {
		IncidentTemplate template = new IncidentTemplate("roll", 1, 0.0,
				curseWeights == null ? Map.of() : curseWeights, List.of(), 1.0, false);
		return rollCurseSet(random, template, grade);
	}

	private static List<String> rollLocalGoals(RandomSource random, IncidentTemplate template) {
		List<String> goals = new ArrayList<>();
		goals.add("investigate_" + template.id());
		if (random.nextBoolean()) {
			goals.add("survive_" + template.id());
		}
		if (random.nextDouble() < 0.20) {
			goals.add("anomaly_" + (1 + random.nextInt(3)));
		}
		return List.copyOf(goals);
	}
	private static double uniform(RandomSource random) {
		long value = random.nextLong();
		value ^= value >>> 30;
		value *= 0xbf58476d1ce4e5b9L;
		value ^= value >>> 27;
		value *= 0x94d049bb133111ebL;
		value ^= value >>> 31;
		return (value >>> 11) * 0x1.0p-53;
	}
}
