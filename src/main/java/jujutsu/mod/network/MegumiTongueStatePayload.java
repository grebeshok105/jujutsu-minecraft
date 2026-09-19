package jujutsu.mod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * Server-authoritative state of Toad's partial tongue for one owner.
 *
 * <p>The phase is explicit so clients can show a real shoot/anchor/retract lifecycle instead of
 * treating every update as an instant line. Anchor coordinates remain stable through retraction,
 * while {@code active=false} is the hard-teardown signal.
 */
public record MegumiTongueStatePayload(
		UUID ownerUuid,
		boolean active,
		int phase,
		double anchorX,
		double anchorY,
		double anchorZ,
		long shotGameTime) implements CustomPacketPayload {
	public static final int SHOOTING = 0;
	public static final int ANCHORED = 1;
	public static final int RETRACTING = 2;

	public static final Type<MegumiTongueStatePayload> TYPE =
			new Type<>(JujutsuMod.id("megumi_tongue_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MegumiTongueStatePayload> STREAM_CODEC =
			CustomPacketPayload.codec(
					(payload, buffer) -> {
						buffer.writeUUID(payload.ownerUuid());
						buffer.writeBoolean(payload.active());
						buffer.writeVarInt(payload.phase());
						buffer.writeDouble(payload.anchorX());
						buffer.writeDouble(payload.anchorY());
						buffer.writeDouble(payload.anchorZ());
						buffer.writeVarLong(payload.shotGameTime());
					},
					buffer -> new MegumiTongueStatePayload(
							buffer.readUUID(),
							buffer.readBoolean(),
							buffer.readVarInt(),
							buffer.readDouble(),
							buffer.readDouble(),
							buffer.readDouble(),
							buffer.readVarLong()));

	public MegumiTongueStatePayload {
		if (ownerUuid == null) {
			throw new IllegalArgumentException("ownerUuid cannot be null");
		}
		if (!isKnownPhase(phase)) {
			throw new IllegalArgumentException("unknown tongue phase: " + phase);
		}
	}

	public static boolean isKnownPhase(int phase) {
		return phase >= SHOOTING && phase <= RETRACTING;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
