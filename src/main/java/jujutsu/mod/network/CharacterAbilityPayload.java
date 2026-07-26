package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/** Client requests a shared active-ability slot; the server resolves the selected character. */
public record CharacterAbilityPayload(int abilityId) implements CustomPacketPayload {
	public static final Type<CharacterAbilityPayload> TYPE = new Type<>(JujutsuMod.id("character_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterAbilityPayload> STREAM_CODEC = CustomPacketPayload.codec(
				CharacterAbilityPayload::write,
				CharacterAbilityPayload::read
	);

	private static CharacterAbilityPayload read(RegistryFriendlyByteBuf buffer) {
		return new CharacterAbilityPayload(buffer.readVarInt());
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(abilityId);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
