package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.fx.HairpinCinematicCamera;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.fx.HairpinScreenOverlay;
import jujutsu.mod.client.fx.NobaraNailFlightManager;
import jujutsu.mod.debug.HairpinDebugLog;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.network.HairpinFxPayload;
import jujutsu.mod.network.HairpinNailFlightPayload;
import jujutsu.mod.network.PreparedNailsPayload;
import jujutsu.mod.network.ProjectJjkNobaraImpulsePayload;
import jujutsu.mod.registry.JujutsuSounds;

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
		ClientPlayNetworking.registerGlobalReceiver(CharacterSelectionSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientCharacterSelectionManager.apply(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCharacterSelectionManager.clear());
	}

	private static void handleProjectJjkImpulse(Minecraft client, ProjectJjkNobaraImpulsePayload payload) {
		if (client.player == null) {
			return;
		}
		if (payload.kind() == ProjectJjkNobaraImpulsePayload.IMPACT) {
			handleProjectJjkImpact(client, payload);
			return;
		}

		double radius = 48.0;
		double distance = client.player.position().distanceTo(payload.origin());
		float proximity = (float) Math.max(0.0, 1.0 - distance / radius);
		if (proximity <= 0.01f) {
			return;
		}

		if (payload.kind() == ProjectJjkNobaraImpulsePayload.HAMMER) {
			playNoFalloff(client, SoundEvents.ANVIL_HIT, 1.0f * proximity, 0.62f, payload.origin());
			playNoFalloff(client, SoundEvents.NETHERITE_BLOCK_HIT, 0.55f * proximity, 0.72f, payload.origin());
			HairpinCinematicCamera.triggerProjectJjkHammer(payload.nailCount(), proximity);
			HairpinScreenOverlay.triggerProjectJjkHammer(proximity);
		}
	}

	private static void handleProjectJjkImpact(Minecraft client, ProjectJjkNobaraImpulsePayload payload) {
		Vec3 origin = payload.origin();
		double distance = client.player.position().distanceTo(origin);
		float proximity = (float) Math.max(0.0, 1.0 - distance / 56.0);
		playNoFalloff(client, JujutsuSounds.PROJECTJJK_WHOOSH_HIT, 0.9f, 0.72f, origin);
		playNoFalloff(client, JujutsuSounds.PROJECTJJK_EXPLODE, 0.52f, 0.78f, origin);
		if (proximity > 0.01f) {
			HairpinCinematicCamera.triggerProjectJjkImpact(payload.nailCount(), proximity);
			HairpinScreenOverlay.triggerProjectJjkImpact(proximity);
		}
	}

	private static void playNoFalloff(Minecraft client, SoundEvent soundEvent, float volume, float pitch, Vec3 origin) {
		client.getSoundManager().play(new SimpleSoundInstance(
				soundEvent.location(),
				SoundSource.PLAYERS,
				Math.max(0.0f, volume),
				pitch,
				RandomSource.create(),
				false,
				0,
				SoundInstance.Attenuation.NONE,
				origin.x,
				origin.y,
				origin.z,
				false
		));
	}
}
