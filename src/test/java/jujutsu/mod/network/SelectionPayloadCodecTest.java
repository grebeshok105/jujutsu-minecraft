package jujutsu.mod.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The first test in the repository that runs a real encode/decode instead of reading source text.
 *
 * <p>These two payloads carry the vessel selection, and nothing covered them. A codec defect here is
 * the worst kind: it compiles, the build stays green, and the damage appears as a client showing the
 * wrong skin or the wrong vessel. Both write two strings back to back, so transposing them in
 * {@code read} is a one-token mistake that no source-text check can see.
 */
class SelectionPayloadCodecTest {
	private static final UUID PLAYER = UUID.fromString("3f2504e0-4f89-11d3-9a0c-0305e82c3301");

	@BeforeAll
	static void bootstrapMinecraft() {
		// Registries and SharedConstants must exist before any Minecraft class that touches them loads.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void selectionSyncKeepsEveryFieldInItsOwnPlace() {
		// Deliberately distinct values: equal ones would let a transposed read pass.
		CharacterSelectionSyncPayload sent = new CharacterSelectionSyncPayload(PLAYER, "nobara", "slim");
		CharacterSelectionSyncPayload received = roundTrip(CharacterSelectionSyncPayload.STREAM_CODEC, sent);

		assertEquals(sent.playerId(), received.playerId(), "player id must survive the wire");
		assertEquals("nobara", received.characterId(), "vessel id must not be read out of the model slot");
		assertEquals("slim", received.modelId(), "model id must not be read out of the vessel slot");
		assertEquals(sent, received);
	}

	@Test
	void selectRequestKeepsItsVesselId() {
		SelectCharacterPayload sent = new SelectCharacterPayload("todo");
		assertEquals(sent, roundTrip(SelectCharacterPayload.STREAM_CODEC, sent));
	}

	@Test
	void writtenLengthCapsAreRealAndDifferent() {
		// The caps are part of the wire format: 32 for a vessel id, 16 for a model id. A value that
		// fits one and not the other proves they were not quietly unified.
		String seventeen = "a".repeat(17);
		assertThrows(RuntimeException.class,
				() -> encode(CharacterSelectionSyncPayload.STREAM_CODEC,
						new CharacterSelectionSyncPayload(PLAYER, "nobara", seventeen)),
				"a 17-character model id must be refused by the 16-character cap");
		assertEquals(seventeen,
				roundTrip(CharacterSelectionSyncPayload.STREAM_CODEC,
						new CharacterSelectionSyncPayload(PLAYER, seventeen, "slim")).characterId(),
				"the same value must pass as a vessel id, whose cap is 32");
	}

	@Test
	void payloadTypeIdsAreStableWireIdentifiers() {
		assertEquals("jujutsumod:character_selection_sync",
				CharacterSelectionSyncPayload.TYPE.id().toString(),
				"renaming a payload type silently breaks every client on the old id");
		assertEquals("jujutsumod:select_character", SelectCharacterPayload.TYPE.id().toString());
	}

	private static <T extends CustomPacketPayload> T roundTrip(
			StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
		RegistryFriendlyByteBuf buffer = encode(codec, payload);
		T decoded = codec.decode(buffer);
		assertEquals(0, buffer.readableBytes(), "the decoder must consume exactly what the encoder wrote");
		return decoded;
	}

	private static <T extends CustomPacketPayload> RegistryFriendlyByteBuf encode(
			StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
		// These codecs never reach for a registry entry, so an empty access is honest here.
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		codec.encode(buffer, payload);
		return buffer;
	}
}
