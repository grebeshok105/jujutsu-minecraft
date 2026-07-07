package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record HairpinNailFlightPayload(
		int seed,
		int ownerEntityId,
		int targetEntityId,
		int nailCount,
		double targetX,
		double targetY,
		double targetZ,
		long startGameTime,
		double nail0X,
		double nail0Y,
		double nail0Z,
		double nail1X,
		double nail1Y,
		double nail1Z,
		double nail2X,
		double nail2Y,
		double nail2Z,
		double nail3X,
		double nail3Y,
		double nail3Z
) implements CustomPacketPayload {
	public static final Type<HairpinNailFlightPayload> TYPE = new Type<>(JujutsuMod.id("hairpin_nail_flight"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HairpinNailFlightPayload> STREAM_CODEC = CustomPacketPayload.codec(
			HairpinNailFlightPayload::write,
			HairpinNailFlightPayload::read
	);

	private static HairpinNailFlightPayload read(RegistryFriendlyByteBuf buffer) {
		return new HairpinNailFlightPayload(
				buffer.readInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readVarInt(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readLong(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble()
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeInt(seed);
		buffer.writeVarInt(ownerEntityId);
		buffer.writeVarInt(targetEntityId);
		buffer.writeVarInt(nailCount);
		buffer.writeDouble(targetX);
		buffer.writeDouble(targetY);
		buffer.writeDouble(targetZ);
		buffer.writeLong(startGameTime);
		buffer.writeDouble(nail0X);
		buffer.writeDouble(nail0Y);
		buffer.writeDouble(nail0Z);
		buffer.writeDouble(nail1X);
		buffer.writeDouble(nail1Y);
		buffer.writeDouble(nail1Z);
		buffer.writeDouble(nail2X);
		buffer.writeDouble(nail2Y);
		buffer.writeDouble(nail2Z);
		buffer.writeDouble(nail3X);
		buffer.writeDouble(nail3Y);
		buffer.writeDouble(nail3Z);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
