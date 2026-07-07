package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.fx.NobaraNailFlightManager;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.PreparedNailsPayload;

public final class JujutsuClientNetworking {
	private JujutsuClientNetworking() {}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(HairpinFxPayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					HairpinDebugLog.info(
							"client received hairpin payload seed={} target={},{},{} startGameTime={}",
							payload.seed(),
							payload.targetX(),
							payload.targetY(),
							payload.targetZ(),
							payload.startGameTime()
					);
					HairpinPlaybackManager.start(payload);
				}));
		ClientPlayNetworking.registerGlobalReceiver(HairpinNailFlightPayload.TYPE, (payload, context) ->
				context.client().execute(() -> NobaraNailFlightManager.startFlight(payload)));
		ClientPlayNetworking.registerGlobalReceiver(PreparedNailsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> NobaraNailFlightManager.showPrepared(payload)));
	}
}
