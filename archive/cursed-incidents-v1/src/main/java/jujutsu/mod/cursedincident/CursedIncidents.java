package jujutsu.mod.cursedincident;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import jujutsu.mod.cursedincident.persist.IncidentSavedData;

/** Single server-registration seam for the incident subsystem. */
public final class CursedIncidents {
	private static boolean registered;

	private CursedIncidents() {
	}

	public static synchronized void registerServerHooks() {
		if (registered) {
			return;
		}
		registered = true;
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel overworld = server.overworld();
			IncidentControl.bindServer(server);
			IncidentControl.bindStore(() -> IncidentSavedData.get(overworld));
			IncidentWiring.rebind();
			IncidentControl.catchUp(overworld);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> IncidentControl.clearRuntimeState());
	}
}
