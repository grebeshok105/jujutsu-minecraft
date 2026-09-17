package jujutsu.mod.cursedincident;

import jujutsu.mod.cursedincident.infection.InfectionSink;
import jujutsu.mod.cursedincident.object.ObjectWiring;
import jujutsu.mod.cursedincident.runtime.IncidentRuntime;
import jujutsu.mod.cursedincident.runtime.PerceptionOverrideRuntime;
/** B3's single registration seam; the main initializer calls this after object wiring. */
public final class IncidentWiring {
	private static boolean registered;

	private IncidentWiring() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		rebind();
		IncidentRuntime.register();
		PerceptionOverrideRuntime.register();
	}

	/** Rebinds process-wide sinks after a server lifecycle reset. */
	public static void rebind() {
		InfectionSink sink = new InfectionSink(ObjectWiring.tracker());
		IncidentRuntime.bindWorldSink(sink);
		IncidentControl.bindWorldSink(sink);
	}
}
