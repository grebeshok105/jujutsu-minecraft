package jujutsu.mod.cursedincident;

import java.util.List;
import java.util.Map;

/** Static data registry for the five v1 incident templates. */
public final class IncidentTemplates {
	public static final IncidentTemplate BLIGHT = new IncidentTemplate(
			"blight", 30, 24.0,
			Map.of("lesser", 60, "common", 30, "greater", 10),
			List.of("dead_fog", "spore_drift"), 1.0, false);

	public static final IncidentTemplate NEST = new IncidentTemplate(
			"nest", 25, 20.0,
			Map.of("lesser", 40, "common", 40, "greater", 20),
			List.of("whisper_den", "brood_hum"), 1.1, true);

	public static final IncidentTemplate CORRUPTION = new IncidentTemplate(
			"corruption", 20, 28.0,
			Map.of("lesser", 50, "common", 35, "greater", 15),
			List.of("ash_fall", "ground_rot"), 0.9, true);

	public static final IncidentTemplate HAUNTING = new IncidentTemplate(
			"haunting", 15, 32.0,
			Map.of("lesser", 70, "common", 25, "greater", 5),
			List.of("wail_wind", "cold_spot"), 0.8, false);

	public static final IncidentTemplate CATACLYSM = new IncidentTemplate(
			"cataclysm", 10, 40.0,
			Map.of("lesser", 30, "common", 40, "greater", 30),
			List.of("dead_fog", "ash_fall", "wail_wind"), 1.3, true);

	public static final List<IncidentTemplate> ALL =
			List.of(BLIGHT, NEST, CORRUPTION, HAUNTING, CATACLYSM);

	private IncidentTemplates() {
	}

	public static IncidentTemplate byId(String id) {
		if (id == null) {
			return null;
		}
		for (IncidentTemplate template : ALL) {
			if (template.id().equalsIgnoreCase(id)) {
				return template;
			}
		}
		return null;
	}
}
