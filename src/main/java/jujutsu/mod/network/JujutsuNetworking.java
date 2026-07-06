package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

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
}
