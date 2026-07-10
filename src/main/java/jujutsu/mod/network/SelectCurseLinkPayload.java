package jujutsu.mod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record SelectCurseLinkPayload(UUID linkId) implements CustomPacketPayload {
	public static final Type<SelectCurseLinkPayload> TYPE = new Type<>(JujutsuMod.id("select_curse_link"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SelectCurseLinkPayload> STREAM_CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeUUID(payload.linkId()), buffer -> new SelectCurseLinkPayload(buffer.readUUID()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
