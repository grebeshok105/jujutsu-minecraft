package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * Client requests a shared active-ability slot, naming the vessel it believed it was casting as.
 *
 * <p>The vessel is on the wire because a slot is an <b>input position</b>, not an ability: the same slot
 * is a teleport for one vessel and a hairpin for another. The character menu applies a switch locally
 * and closes before the server has confirmed it, so for the length of that round trip a key press would
 * otherwise be executed by the vessel the player just left. Stamping what the client believed lets the
 * server refuse rather than cast the wrong thing.
 *
 * <p>The server still resolves the real vessel itself and never trusts this field for anything but the
 * comparison — it is a claim to be checked, not an instruction.
 */
public record CharacterAbilityPayload(int abilityId, String characterId) implements CustomPacketPayload {
	public static final Type<CharacterAbilityPayload> TYPE = new Type<>(JujutsuMod.id("character_ability"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CharacterAbilityPayload> STREAM_CODEC = CustomPacketPayload.codec(
				CharacterAbilityPayload::write,
				CharacterAbilityPayload::read
	);

	private static CharacterAbilityPayload read(RegistryFriendlyByteBuf buffer) {
		return new CharacterAbilityPayload(buffer.readVarInt(), buffer.readUtf(32));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(abilityId);
		buffer.writeUtf(characterId, 32);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
