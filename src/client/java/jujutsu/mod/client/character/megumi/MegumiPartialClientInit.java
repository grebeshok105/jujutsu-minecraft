package jujutsu.mod.client.character.megumi;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.tongue.TongueClientState;
import jujutsu.mod.client.tongue.TongueRenderer;
import jujutsu.mod.network.MegumiTongueStatePayload;
import jujutsu.mod.network.MegumiWingsStatePayload;

/** Client registration seam for Megumi's two partial manifestations. */
public final class MegumiPartialClientInit {
	private static boolean registered;

	private MegumiPartialClientInit() {}

	/** Installs receivers, phase ticks, world rendering, and disconnect cleanup exactly once. */
	public static synchronized void register() {
		if (registered) {
			return;
		}
		registered = true;
		ClientPlayNetworking.registerGlobalReceiver(MegumiTongueStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> TongueClientState.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(MegumiWingsStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> MegumiWingsState.apply(payload)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			TongueClientState.tick();
			MegumiWingsState.tick();
		});
		TongueRenderer.register();
		jujutsu.mod.client.render.megumi.MegumiToadTongueRenderer.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TongueClientState.clear();
			MegumiWingsState.clear();
		});
		// Same reason as the disconnect clear: a dimension change swaps the ClientLevel, and
		// anchored tongue/wing entries keyed by owner UUID would otherwise outlive the level.
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE
				.register((client, world) -> {
					TongueClientState.clear();
					MegumiWingsState.clear();
				});
	}
}
