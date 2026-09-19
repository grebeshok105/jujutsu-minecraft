package jujutsu.mod.network;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * Server-authoritative snapshot of one incident work center (parent zone or a secondary
 * node) for the client zone renderer. Keyed client-side by
 * {@code (dimension, incidentId, nodeId)}; {@code nodeId} is the zero UUID for the parent.
 * {@code active=false} is the cleanup signal for exactly that one key — a parent's
 * deactivation never clears sibling/child node entries (R20).
 */
public record IncidentZoneStatePayload(
		String dimension,
		UUID incidentId,
		UUID nodeId,
		double centerX,
		double centerY,
		double centerZ,
		double radius,
		int stage,
		String atmosphereId,
		int sealTier,
		int sealIntegrity,
		boolean active) implements CustomPacketPayload {

	public static final UUID PARENT_NODE = new UUID(0L, 0L);

	public static final Type<IncidentZoneStatePayload> TYPE =
			new Type<>(JujutsuMod.id("incident_zone_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, IncidentZoneStatePayload> STREAM_CODEC =
			StreamCodec.of(
					(buffer, payload) -> {
						buffer.writeUtf(payload.dimension());
						UUIDUtil.STREAM_CODEC.encode(buffer, payload.incidentId());
						UUIDUtil.STREAM_CODEC.encode(buffer, payload.nodeId());
						buffer.writeDouble(payload.centerX());
						buffer.writeDouble(payload.centerY());
						buffer.writeDouble(payload.centerZ());
						buffer.writeDouble(payload.radius());
						ByteBufCodecs.VAR_INT.encode(buffer, payload.stage());
						buffer.writeUtf(payload.atmosphereId());
						ByteBufCodecs.VAR_INT.encode(buffer, payload.sealTier());
						ByteBufCodecs.VAR_INT.encode(buffer, payload.sealIntegrity());
						buffer.writeBoolean(payload.active());
					},
					buffer -> new IncidentZoneStatePayload(
							buffer.readUtf(),
							UUIDUtil.STREAM_CODEC.decode(buffer),
							UUIDUtil.STREAM_CODEC.decode(buffer),
							buffer.readDouble(),
							buffer.readDouble(),
							buffer.readDouble(),
							buffer.readDouble(),
							ByteBufCodecs.VAR_INT.decode(buffer),
							buffer.readUtf(),
							ByteBufCodecs.VAR_INT.decode(buffer),
							ByteBufCodecs.VAR_INT.decode(buffer),
							buffer.readBoolean()));

	public IncidentZoneStatePayload {
		dimension = dimension == null ? "" : dimension;
		nodeId = nodeId == null ? PARENT_NODE : nodeId;
		atmosphereId = atmosphereId == null ? "" : atmosphereId;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
