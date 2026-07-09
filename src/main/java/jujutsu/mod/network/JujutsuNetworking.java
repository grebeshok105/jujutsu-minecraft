package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraActions;
import jujutsu.mod.vfx.VfxCue;

public final class JujutsuNetworking {
	private JujutsuNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(ProjectJjkNobaraImpulsePayload.TYPE, ProjectJjkNobaraImpulsePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(VfxCuePayload.TYPE, VfxCuePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(CharacterSelectionSyncPayload.TYPE, CharacterSelectionSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SelectCharacterPayload.TYPE, SelectCharacterPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(NobaraActionPayload.TYPE, NobaraActionPayload.STREAM_CODEC);
		registerServerReceivers();
	}

	private static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.TYPE, (payload, context) ->
				context.server().execute(() -> CharacterSelectionManager.select(context.player(), JujutsuCharacter.byId(payload.characterId()))));
		ServerPlayNetworking.registerGlobalReceiver(NobaraActionPayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleNobaraAction(context.player(), payload)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CharacterSelectionManager.syncTo(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CharacterSelectionManager.clear(handler.player));
	}

	private static void handleNobaraAction(ServerPlayer player, NobaraActionPayload payload) {
		ProjectJjkNobaraActions.tryCast(player, payload.action(), true);
	}

	public static int broadcastProjectJjkImpulse(ServerLevel level, Vec3 center, double radius, ProjectJjkNobaraImpulsePayload payload) {
		double radiusSqr = radius * radius;
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, ProjectJjkNobaraImpulsePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	public static boolean sendProjectJjkImpulse(ServerPlayer player, ProjectJjkNobaraImpulsePayload payload) {
		if (!ServerPlayNetworking.canSend(player, ProjectJjkNobaraImpulsePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	public static int broadcastVfxCue(ServerLevel level, Vec3 center, double radius, VfxCue cue) {
		double radiusSqr = radius * radius;
		VfxCuePayload payload = new VfxCuePayload(cue);
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	public static boolean sendVfxCue(ServerPlayer player, VfxCue cue) {
		if (!ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new VfxCuePayload(cue));
		return true;
	}

}
