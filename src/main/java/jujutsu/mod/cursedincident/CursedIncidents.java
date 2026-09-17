package jujutsu.mod.cursedincident;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Single server-registration seam for the cursed-incident subsystem (issue #110) —
 * same shape as {@code CursedSpirits.registerServerHooks()}. Called once from
 * {@code JujutsuMod.onInitialize()}.
 *
 * <p>Init order inside: persistence load + catch-up first, then the world sink and
 * dwell/object wiring (bound by the object/infection wiring classes), then the
 * SERVER_STOPPING clear.
 */
public final class CursedIncidents {

	private CursedIncidents() {
	}

	public static void registerServerHooks() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> IncidentControl.clearRuntimeState());
	}
}
