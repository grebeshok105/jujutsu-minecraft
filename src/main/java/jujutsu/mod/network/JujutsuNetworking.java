package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.debug.HairpinDebugLog;

public final class JujutsuNetworking {
	private JujutsuNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(HairpinFxPayload.TYPE, HairpinFxPayload.STREAM_CODEC);
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
}
