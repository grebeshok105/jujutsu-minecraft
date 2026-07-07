package jujutsu.mod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

public record CharacterSelectionSyncPayload(UUID playerId, String characterId, String modelId) implements CustomPacketPayload {
	public static final Type<CharacterSelectionSyncPayload> TYPE = new Type<>(JujutsuMod.id("character_selection_sync"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterSelectionSyncPayload> STREAM_CODEC = CustomPacketPayload.codec(
			CharacterSelectionSyncPayload::write,
			CharacterSelectionSyncPayload::read
	);

	private static CharacterSelectionSyncPayload read(RegistryFriendlyByteBuf buffer) {
		return new CharacterSelectionSyncPayload(buffer.readUUID(), buffer.readUtf(32), buffer.readUtf(16));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUUID(playerId);
		buffer.writeUtf(characterId, 32);
		buffer.writeUtf(modelId, 16);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
