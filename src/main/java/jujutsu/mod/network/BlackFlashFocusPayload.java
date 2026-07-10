package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record BlackFlashFocusPayload(boolean focused) implements CustomPacketPayload {
	public static final Type<BlackFlashFocusPayload> TYPE = new Type<>(JujutsuMod.id("black_flash_focus"));
	public static final StreamCodec<RegistryFriendlyByteBuf, BlackFlashFocusPayload> STREAM_CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeBoolean(payload.focused()), buffer -> new BlackFlashFocusPayload(buffer.readBoolean()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
