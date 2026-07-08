package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record ProjectJjkTargetMarkPayload(int targetEntityId, int marks, long expiresGameTime) implements CustomPacketPayload {
	public static final Type<ProjectJjkTargetMarkPayload> TYPE = new Type<>(JujutsuMod.id("projectjjk_target_mark"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ProjectJjkTargetMarkPayload> STREAM_CODEC = CustomPacketPayload.codec(
			ProjectJjkTargetMarkPayload::write,
			ProjectJjkTargetMarkPayload::read
	);

	private static ProjectJjkTargetMarkPayload read(RegistryFriendlyByteBuf buffer) {
		return new ProjectJjkTargetMarkPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readLong());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(targetEntityId);
		buffer.writeVarInt(marks);
		buffer.writeLong(expiresGameTime);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
