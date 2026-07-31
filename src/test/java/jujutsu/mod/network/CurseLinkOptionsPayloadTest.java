package jujutsu.mod.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CurseLinkOptionsPayloadTest {
	private static final UUID SOURCE = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void negativeCountIsRejectedBeforeAllocation() {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		buffer.writeVarInt(-1);

		assertThrows(RuntimeException.class, () -> CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer));
	}

	@Test
	void countAboveMaximumIsRejectedBeforeAllocation() {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		buffer.writeVarInt(CurseLinkOptionsPayload.MAX_ENTRIES + 1);

		assertThrows(RuntimeException.class, () -> CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer));
	}

	@Test
	void overLengthTechniqueIdRejectsTheWholePayload() {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		buffer.writeVarInt(1);
		writeRawEntry(buffer, UUID.randomUUID(), SOURCE, "a".repeat(CurseLinkOptionsPayload.MAX_TECHNIQUE_ID_LENGTH + 1));

		assertThrows(RuntimeException.class, () -> CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer));
	}

	@Test
	void malformedTechniqueIdDropsOnlyThatEntry() {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		buffer.writeVarInt(2);
		writeRawEntry(buffer, UUID.randomUUID(), SOURCE, "not a resource location");
		CurseLinkOptionsPayload.Entry valid = entry(1);
		writeRawEntry(buffer, valid.linkId(), valid.sourceId(), valid.techniqueId().toString());

		CurseLinkOptionsPayload decoded = CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer);

		assertEquals(List.of(valid), decoded.entries());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void entryAfterMalformedTechniqueIdRemainsAligned() {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		buffer.writeVarInt(2);
		writeRawEntry(buffer, UUID.randomUUID(), SOURCE, "invalid id");
		CurseLinkOptionsPayload.Entry valid = new CurseLinkOptionsPayload.Entry(
				UUID.fromString("00000000-0000-0000-0000-000000000002"),
				UUID.fromString("00000000-0000-0000-0000-000000000003"),
				ResourceLocation.parse("jujutsumod:following_entry"));
		writeRawEntry(buffer, valid.linkId(), valid.sourceId(), valid.techniqueId().toString());

		CurseLinkOptionsPayload decoded = CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer);

		assertEquals(1, decoded.entries().size());
		assertEquals(valid.linkId(), decoded.entries().getFirst().linkId());
		assertEquals(valid.sourceId(), decoded.entries().getFirst().sourceId());
		assertEquals(valid.techniqueId(), decoded.entries().getFirst().techniqueId());
		assertEquals(0, buffer.readableBytes());
	}

	@Test
	void writerRefusesMoreThanMaximumEntries() {
		List<CurseLinkOptionsPayload.Entry> entries = IntStream.range(0, CurseLinkOptionsPayload.MAX_ENTRIES + 1)
				.mapToObj(CurseLinkOptionsPayloadTest::entry)
				.toList();

		assertThrows(IllegalArgumentException.class, () -> encode(new CurseLinkOptionsPayload(entries)));
	}

	@Test
	void writerRefusesOverLengthTechniqueId() {
		ResourceLocation oversized = ResourceLocation.parse("jujutsumod:" + "a".repeat(CurseLinkOptionsPayload.MAX_TECHNIQUE_ID_LENGTH));
		CurseLinkOptionsPayload.Entry entry = new CurseLinkOptionsPayload.Entry(UUID.randomUUID(), SOURCE, oversized);

		assertThrows(IllegalArgumentException.class, () -> encode(new CurseLinkOptionsPayload(List.of(entry))));
	}

	@Test
	void emptyPayloadRoundTrips() {
		assertEquals(new CurseLinkOptionsPayload(List.of()), roundTrip(new CurseLinkOptionsPayload(List.of())));
	}

	@Test
	void oneEntryPayloadRoundTrips() {
		CurseLinkOptionsPayload payload = new CurseLinkOptionsPayload(List.of(entry(0)));

		assertEquals(payload, roundTrip(payload));
	}

	@Test
	void maximumSizePayloadRoundTrips() {
		List<CurseLinkOptionsPayload.Entry> entries = IntStream.range(0, CurseLinkOptionsPayload.MAX_ENTRIES)
				.mapToObj(CurseLinkOptionsPayloadTest::entry)
				.toList();

		assertEquals(new CurseLinkOptionsPayload(entries), roundTrip(new CurseLinkOptionsPayload(entries)));
	}

	@Test
	void validPayloadIsByteIdenticalAfterDecodeAndReencode() {
		CurseLinkOptionsPayload sent = new CurseLinkOptionsPayload(List.of(entry(4), entry(5)));
		RegistryFriendlyByteBuf encoded = encode(sent);
		byte[] before = readableBytes(encoded);

		CurseLinkOptionsPayload decoded = CurseLinkOptionsPayload.STREAM_CODEC.decode(encoded);
		assertEquals(0, encoded.readableBytes());

		RegistryFriendlyByteBuf reencoded = encode(decoded);
		assertArrayEquals(before, readableBytes(reencoded));
	}

	@Test
	void validDecodeConsumesTheBuffer() {
		RegistryFriendlyByteBuf buffer = encode(new CurseLinkOptionsPayload(List.of(entry(8))));

		CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer);

		assertEquals(0, buffer.readableBytes());
	}

	private static CurseLinkOptionsPayload.Entry entry(int index) {
		return new CurseLinkOptionsPayload.Entry(
				UUID.nameUUIDFromBytes(("link-" + index).getBytes()),
				UUID.nameUUIDFromBytes(("source-" + index).getBytes()),
				ResourceLocation.fromNamespaceAndPath("jujutsumod", "technique_" + index));
	}

	private static void writeRawEntry(RegistryFriendlyByteBuf buffer, UUID linkId, UUID sourceId, String techniqueId) {
		buffer.writeUUID(linkId);
		buffer.writeUUID(sourceId);
		buffer.writeUtf(techniqueId);
	}

	private static CurseLinkOptionsPayload roundTrip(CurseLinkOptionsPayload sent) {
		RegistryFriendlyByteBuf buffer = encode(sent);
		CurseLinkOptionsPayload received = CurseLinkOptionsPayload.STREAM_CODEC.decode(buffer);
		assertEquals(0, buffer.readableBytes());
		return received;
	}

	private static RegistryFriendlyByteBuf encode(CurseLinkOptionsPayload payload) {
		RegistryFriendlyByteBuf buffer = rawBuffer();
		CurseLinkOptionsPayload.STREAM_CODEC.encode(buffer, payload);
		return buffer;
	}

	private static RegistryFriendlyByteBuf rawBuffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}

	private static byte[] readableBytes(RegistryFriendlyByteBuf buffer) {
		byte[] bytes = new byte[buffer.readableBytes()];
		buffer.getBytes(buffer.readerIndex(), bytes);
		return bytes;
	}
}
