package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.debug.HairpinDebugLog;

public final class JujutsuNetworking {
	private JujutsuNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(HairpinFxPayload.TYPE, HairpinFxPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(HairpinNailFlightPayload.TYPE, HairpinNailFlightPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(PreparedNailsPayload.TYPE, PreparedNailsPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(ProjectJjkNobaraImpulsePayload.TYPE, ProjectJjkNobaraImpulsePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(ProjectJjkCursedEnergyPayload.TYPE, ProjectJjkCursedEnergyPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(CharacterSelectionSyncPayload.TYPE, CharacterSelectionSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SelectCharacterPayload.TYPE, SelectCharacterPayload.STREAM_CODEC);
		registerServerReceivers();
	}

	private static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.TYPE, (payload, context) ->
				context.server().execute(() -> CharacterSelectionManager.select(context.player(), JujutsuCharacter.byId(payload.characterId()))));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> CharacterSelectionManager.syncTo(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CharacterSelectionManager.clear(handler.player));
	}

	public static boolean sendHairpin(ServerPlayer player, HairpinFxPayload payload) {
		if (!ServerPlayNetworking.canSend(player, HairpinFxPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	public static int broadcastHairpin(ServerLevel level, Vec3 center, double radius, HairpinFxPayload payload) {
		double radiusSqr = radius * radius;
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (sendHairpin(player, payload)) {
				HairpinDebugLog.info("server sent hairpin payload to={} seed={} center={}", player.getGameProfile().getName(), payload.seed(), HairpinDebugLog.vec(center));
				sent++;
			} else {
				HairpinDebugLog.info("server could not send hairpin payload to={} seed={} canSend=false", player.getGameProfile().getName(), payload.seed());
			}
		}
		return sent;
	}

	public static int broadcastNailFlight(ServerLevel level, Vec3 center, double radius, HairpinNailFlightPayload payload) {
		double radiusSqr = radius * radius;
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, HairpinNailFlightPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	public static int broadcastPreparedNails(ServerLevel level, Vec3 center, double radius, PreparedNailsPayload payload) {
		double radiusSqr = radius * radius;
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, PreparedNailsPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
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

	public static boolean sendCursedEnergy(ServerPlayer player, ProjectJjkCursedEnergyPayload payload) {
		if (!ServerPlayNetworking.canSend(player, ProjectJjkCursedEnergyPayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}
}
