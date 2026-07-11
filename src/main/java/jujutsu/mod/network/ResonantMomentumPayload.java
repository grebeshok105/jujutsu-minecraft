package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record ResonantMomentumPayload(int remainingTicks) implements CustomPacketPayload {
	public static final Type<ResonantMomentumPayload> TYPE = new Type<>(JujutsuMod.id("resonant_momentum"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ResonantMomentumPayload> STREAM_CODEC = CustomPacketPayload.codec(
			(payload, buffer) -> buffer.writeVarInt(Math.max(0, payload.remainingTicks())),
			buffer -> new ResonantMomentumPayload(buffer.readVarInt()));
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
