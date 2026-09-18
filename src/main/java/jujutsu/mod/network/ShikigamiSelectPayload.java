package jujutsu.mod.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import jujutsu.mod.JujutsuMod;

/**
 * The client's click: which shikigami the player just picked from the selector strip.
 *
 * <p>Vessel-neutral by name and by shape, on purpose. The payload carries an id string, not a roster
 * constant and not a slot index, so the shared network package never names a vessel's type — the
 * receiver hands the id to the player's own {@code CharacterDefinition}, and a vessel that has no
 * such roster answers {@code false}. That is also why the two selector packets live here rather than
 * beside the roster: a payload may not sit inside a vessel package, and a vessel-neutral packet has
 * exactly one legal home.
 */
public record ShikigamiSelectPayload(String shikigamiId) implements CustomPacketPayload {
	/** Every legal roster id is a single short word; the cap is part of the wire format. */
	public static final int MAX_ID_LENGTH = 16;
	public static final Type<ShikigamiSelectPayload> TYPE = new Type<>(JujutsuMod.id("shikigami_select"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ShikigamiSelectPayload> STREAM_CODEC = CustomPacketPayload.codec(
			ShikigamiSelectPayload::write, ShikigamiSelectPayload::read);

	private static ShikigamiSelectPayload read(RegistryFriendlyByteBuf buffer) {
		return new ShikigamiSelectPayload(buffer.readUtf(MAX_ID_LENGTH));
	}

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeUtf(shikigamiId, MAX_ID_LENGTH);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
