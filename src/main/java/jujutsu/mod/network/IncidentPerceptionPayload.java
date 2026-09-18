package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/** Server-authoritative per-player critical-zone visibility bit. */
public record IncidentPerceptionPayload(boolean inCriticalZone) implements CustomPacketPayload {
	public static final Type<IncidentPerceptionPayload> TYPE = new Type<>(JujutsuMod.id("incident_perception"));
	public static final StreamCodec<RegistryFriendlyByteBuf, IncidentPerceptionPayload> STREAM_CODEC =
			CustomPacketPayload.codec(
					(payload, buffer) -> buffer.writeBoolean(payload.inCriticalZone()),
					buffer -> new IncidentPerceptionPayload(buffer.readBoolean()));

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
