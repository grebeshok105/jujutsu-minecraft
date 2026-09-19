package jujutsu.mod.client.cursedincident;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.network.IncidentPerceptionPayload;

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
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientPerceptionState.clear());
		IncidentAtmosphere.register();
		CursedObjectRenderer.install();
	}
}
