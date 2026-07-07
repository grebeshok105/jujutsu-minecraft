package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record SelectCharacterPayload(String characterId) implements CustomPacketPayload {
	public static final Type<SelectCharacterPayload> TYPE = new Type<>(JujutsuMod.id("select_character"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectCharacterPayload> STREAM_CODEC = CustomPacketPayload.codec(
			SelectCharacterPayload::write,
			SelectCharacterPayload::read
	);

	private static SelectCharacterPayload read(RegistryFriendlyByteBuf buffer) {
		return new SelectCharacterPayload(buffer.readUtf(32));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(characterId, 32);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
