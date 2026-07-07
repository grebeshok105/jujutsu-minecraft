package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * Owner-only sync of the cursed-energy pool plus the current resonance link state, feeding the
 * custom HUD. Sent when values change materially or on a slow heartbeat.
 */
public record ProjectJjkCursedEnergyPayload(
		float current,
		float max,
		int linkedMarks,
		boolean linked
) implements CustomPacketPayload {
	public static final Type<ProjectJjkCursedEnergyPayload> TYPE = new Type<>(JujutsuMod.id("projectjjk_cursed_energy"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProjectJjkCursedEnergyPayload> STREAM_CODEC = CustomPacketPayload.codec(
			ProjectJjkCursedEnergyPayload::write,
			ProjectJjkCursedEnergyPayload::read
	);

	private static ProjectJjkCursedEnergyPayload read(RegistryFriendlyByteBuf buffer) {
		return new ProjectJjkCursedEnergyPayload(
				buffer.readFloat(),
				buffer.readFloat(),
				buffer.readVarInt(),
				buffer.readBoolean()
		);
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeFloat(current);
		buffer.writeFloat(max);
		buffer.writeVarInt(linkedMarks);
		buffer.writeBoolean(linked);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
