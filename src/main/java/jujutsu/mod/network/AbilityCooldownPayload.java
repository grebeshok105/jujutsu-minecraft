package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/** Server confirmation of a character ability cooldown; client uses it only to suppress redundant input packets. */
public record AbilityCooldownPayload(String characterId, int abilityId, int remainingTicks) implements CustomPacketPayload {
	public static final Type<AbilityCooldownPayload> TYPE = new Type<>(JujutsuMod.id("ability_cooldown"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AbilityCooldownPayload> STREAM_CODEC = CustomPacketPayload.codec(
				AbilityCooldownPayload::write,
				AbilityCooldownPayload::read
	);

	private static AbilityCooldownPayload read(RegistryFriendlyByteBuf buffer) {
		return new AbilityCooldownPayload(buffer.readUtf(32), buffer.readVarInt(), Math.max(0, buffer.readVarInt()));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(characterId, 32);
		buffer.writeVarInt(abilityId);
		buffer.writeVarInt(Math.max(0, remainingTicks));
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
