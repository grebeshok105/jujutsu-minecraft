package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record PreparedNailsPayload(
		int seed,
		int playerEntityId,
		int nailCount,
		long startGameTime
) implements CustomPacketPayload {
	public static final Type<PreparedNailsPayload> TYPE = new Type<>(JujutsuMod.id("prepared_nails"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PreparedNailsPayload> STREAM_CODEC = CustomPacketPayload.codec(
			PreparedNailsPayload::write,
			PreparedNailsPayload::read
	);

	private static PreparedNailsPayload read(RegistryFriendlyByteBuf buffer) {
		return new PreparedNailsPayload(buffer.readInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readLong());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(seed);
		buffer.writeVarInt(playerEntityId);
		buffer.writeVarInt(nailCount);
		buffer.writeLong(startGameTime);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
