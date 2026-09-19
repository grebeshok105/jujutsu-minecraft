package jujutsu.mod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/** Server-authoritative presentation state for one player's Nue wings. */
public record MegumiWingsStatePayload(
		UUID ownerUuid,
		boolean active,
		int phase,
		long phaseStartGameTime) implements CustomPacketPayload {
	public static final int MATERIALIZING = 0;
	public static final int GROUND_FOLDED = 1;
	public static final int FLYING = 2;
	public static final int FOLDING = 3;

	public static final Type<MegumiWingsStatePayload> TYPE =
			new Type<>(JujutsuMod.id("megumi_wings_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MegumiWingsStatePayload> STREAM_CODEC =
			CustomPacketPayload.codec(MegumiWingsStatePayload::write, MegumiWingsStatePayload::read);

	public MegumiWingsStatePayload {
		if (ownerUuid == null) {
			throw new IllegalArgumentException("ownerUuid cannot be null");
		}
	}

	public static boolean isKnownPhase(int phase) {
		return phase >= MATERIALIZING && phase <= FOLDING;
	}

	private static MegumiWingsStatePayload read(RegistryFriendlyByteBuf buffer) {
		return new MegumiWingsStatePayload(
				buffer.readUUID(),
				buffer.readBoolean(),
				buffer.readVarInt(),
				buffer.readVarLong());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(ownerUuid);
		buffer.writeBoolean(active);
		buffer.writeVarInt(phase);
		buffer.writeVarLong(phaseStartGameTime);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
