package jujutsu.mod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.JujutsuMod;

public record CurseLinkOptionsPayload(List<Entry> entries) implements CustomPacketPayload {
	public static final Type<CurseLinkOptionsPayload> TYPE = new Type<>(JujutsuMod.id("curse_link_options"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CurseLinkOptionsPayload> STREAM_CODEC = CustomPacketPayload.codec(CurseLinkOptionsPayload::write, CurseLinkOptionsPayload::read);
	public CurseLinkOptionsPayload { entries = List.copyOf(entries); }
	private static CurseLinkOptionsPayload read(RegistryFriendlyByteBuf buffer) {
		int size = buffer.readVarInt();
		List<Entry> entries = new ArrayList<>(size);
		for (int i = 0; i < size; i++) entries.add(new Entry(buffer.readUUID(), buffer.readUUID(), ResourceLocation.parse(buffer.readUtf())));
		return new CurseLinkOptionsPayload(entries);
	}
	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(entries.size());
		for (Entry entry : entries) { buffer.writeUUID(entry.linkId()); buffer.writeUUID(entry.sourceId()); buffer.writeUtf(entry.techniqueId().toString()); }
	}
	@Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
	public record Entry(UUID linkId, UUID sourceId, ResourceLocation techniqueId) {}
}
