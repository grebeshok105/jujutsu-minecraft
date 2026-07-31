package jujutsu.mod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ResourceLocationException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

public record CurseLinkOptionsPayload(List<Entry> entries) implements CustomPacketPayload {
	/** Defensive bounds for untrusted input; the current curse-link registry has no natural maximum. */
	public static final int MAX_ENTRIES = 64;
	public static final int MAX_TECHNIQUE_ID_LENGTH = 256;
	public static final Type<CurseLinkOptionsPayload> TYPE = new Type<>(JujutsuMod.id("curse_link_options"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CurseLinkOptionsPayload> STREAM_CODEC = CustomPacketPayload.codec(CurseLinkOptionsPayload::write, CurseLinkOptionsPayload::read);
	public CurseLinkOptionsPayload { entries = List.copyOf(entries); }
	private static CurseLinkOptionsPayload read(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		if (size < 0 || size > MAX_ENTRIES) throw new IllegalArgumentException("Invalid curse-link entry count: " + size);
		List<Entry> entries = new ArrayList<>();
		boolean malformedEntryLogged = false;
		for (int i = 0; i < size; i++) {
			UUID linkId = buffer.readUUID();
			UUID sourceId = buffer.readUUID();
			String techniqueId = buffer.readUtf(MAX_TECHNIQUE_ID_LENGTH);
			try {
				entries.add(new Entry(linkId, sourceId, ResourceLocation.parse(techniqueId)));
			} catch (ResourceLocationException exception) {
				if (!malformedEntryLogged) {
					JujutsuMod.LOGGER.warn("Dropped malformed technique id from CurseLinkOptionsPayload");
					malformedEntryLogged = true;
				}
			}
		}
		return new CurseLinkOptionsPayload(entries);
	}
	private void write(RegistryFriendlyByteBuf buffer) {
		validateForWrite();
		buffer.writeVarInt(entries.size());
		for (Entry entry : entries) {
			buffer.writeUUID(entry.linkId());
			buffer.writeUUID(entry.sourceId());
			buffer.writeUtf(entry.techniqueId().toString(), MAX_TECHNIQUE_ID_LENGTH);
		}
	}
	private void validateForWrite() {
		if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many curse-link entries: " + entries.size());
		for (Entry entry : entries) {
			String techniqueId = entry.techniqueId().toString();
			if (techniqueId.length() > MAX_TECHNIQUE_ID_LENGTH) {
				throw new IllegalArgumentException("Technique id is too long: " + techniqueId.length());
			}
		}
	}
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	public record Entry(UUID linkId, UUID sourceId, ResourceLocation techniqueId) {}
}
