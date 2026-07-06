package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.network.HairpinFxPayload;

public final class JujutsuClientNetworking {
	private JujutsuClientNetworking() {}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(HairpinFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> HairpinPlaybackManager.start(payload)));
	}
}
