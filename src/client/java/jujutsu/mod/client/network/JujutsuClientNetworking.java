package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import jujutsu.mod.client.fx.HairpinCinematicCamera;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.fx.HairpinScreenOverlay;
import jujutsu.mod.client.fx.NobaraNailFlightManager;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.PreparedNailsPayload;
import jujutsu.mod.network.ProjectJjkNobaraImpulsePayload;

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
		ClientPlayNetworking.registerGlobalReceiver(ProjectJjkNobaraImpulsePayload.TYPE, (payload, context) ->
				context.client().execute(() -> handleProjectJjkImpulse(context.client(), payload)));
	}

	private static void handleProjectJjkImpulse(Minecraft client, ProjectJjkNobaraImpulsePayload payload) {
		if (client.player == null) {
			return;
		}
		double radius = payload.kind() == ProjectJjkNobaraImpulsePayload.HAMMER ? 40.0 : 56.0;
		double distance = client.player.position().distanceTo(payload.origin());
		float proximity = (float) Math.max(0.0, 1.0 - distance / radius);
		if (proximity <= 0.01f) {
			return;
		}

		if (payload.kind() == ProjectJjkNobaraImpulsePayload.HAMMER) {
			HairpinCinematicCamera.triggerProjectJjkHammer(payload.nailCount(), proximity);
			HairpinScreenOverlay.triggerProjectJjkHammer(proximity);
			return;
		}
		if (payload.kind() == ProjectJjkNobaraImpulsePayload.IMPACT) {
			HairpinCinematicCamera.triggerProjectJjkImpact(payload.nailCount(), proximity);
			HairpinScreenOverlay.triggerProjectJjkImpact(proximity);
		}
	}
}
