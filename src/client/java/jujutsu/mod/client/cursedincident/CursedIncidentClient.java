package jujutsu.mod.client.cursedincident;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import jujutsu.mod.network.IncidentPerceptionPayload;
import jujutsu.mod.network.IncidentZoneStatePayload;

/** Client bootstrap for perception state, atmosphere recipes and cursed-object rendering. */
public final class CursedIncidentClient {
	private static boolean registered;

	private CursedIncidentClient() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientPlayNetworking.registerGlobalReceiver(IncidentPerceptionPayload.TYPE,
				(payload, context) -> context.client().execute(() -> ClientPerceptionState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(IncidentZoneStatePayload.TYPE,
				(payload, context) -> context.client().execute(() -> IncidentZoneState.apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			IncidentZoneState.tick();
			IncidentZoneRenderer.resetMoteBudget();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientPerceptionState.clear();
			IncidentZoneState.clear();
			IncidentZoneRenderer.clearCache();
		});
		IncidentAtmosphere.register();
		IncidentZoneRenderer.register();
		CursedObjectRenderer.install();
	}
}
