package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * Server→client state of Toad's partial tongue (issue #108): whether it is anchored and where.
 * The client owns the pull physics (a server-side velocity on a player is a fiction), so this
 * payload is the whole authoritative channel — anchor on attach, {@code active=false} on break
 * or release.
 */
public record MegumiTongueStatePayload(boolean active, double anchorX, double anchorY, double anchorZ)
		implements CustomPacketPayload {
	public static final Type<MegumiTongueStatePayload> TYPE =
			new Type<>(JujutsuMod.id("megumi_tongue_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MegumiTongueStatePayload> STREAM_CODEC =
			CustomPacketPayload.codec(
					(payload, buffer) -> {
						buffer.writeBoolean(payload.active());
						buffer.writeDouble(payload.anchorX());
						buffer.writeDouble(payload.anchorY());
						buffer.writeDouble(payload.anchorZ());
					},
					buffer -> new MegumiTongueStatePayload(
							buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
