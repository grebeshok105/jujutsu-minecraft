package jujutsu.mod.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jujutsu.mod.character.megumi.MegumiShikigami;
import jujutsu.mod.character.megumi.MegumiShikigamiSlotState;

/**
 * Encode/decode round-trips for the two selector payloads, running a real buffer instead of reading
 * source text (precedent: {@link SelectionPayloadCodecTest}).
 *
 * <p>The state snapshot is the interesting one: it is a parallel-array wire format, so a transposed
 * write — a code read out of the deadline array, or a snapshot whose slot count does not match the
 * roster — compiles, keeps the build green, and shows up only as a client marking the wrong slot
 * summoned or cooling. The arrays carry deliberately distinct values so a shifted read cannot pass,
 * and the state codes cover the whole {@link MegumiShikigamiSlotState} value space, not just the two
 * that are reachable today.
 */
class ShikigamiPayloadCodecTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		// Registries and SharedConstants must exist before any Minecraft class that touches them loads.
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void selectRequestKeepsItsShikigamiId() {
		ShikigamiSelectPayload sent = new ShikigamiSelectPayload("rabbits");
		assertEquals(sent, roundTrip(ShikigamiSelectPayload.STREAM_CODEC, sent));
	}

	@Test
	void selectRequestRefusesAnIdLongerThanItsCap() {
		// The 16-character cap is part of the wire format, and every legal id is far shorter than it.
		assertThrows(RuntimeException.class,
				() -> encode(ShikigamiSelectPayload.STREAM_CODEC, new ShikigamiSelectPayload("a".repeat(17))),
				"a 17-character shikigami id must be refused by the 16-character cap");
	}

	@Test
	void selectRequestRefusesAnOverlongIdOnTheDecodeSideToo() {
		// The cap must hold against a client that skips the encoder: the length prefix and the bytes are
		// written by hand, exactly as a hand-rolled packet would arrive.
		RegistryFriendlyByteBuf buffer = buffer();
		buffer.writeVarInt(17);
		buffer.writeBytes("a".repeat(17).getBytes(StandardCharsets.UTF_8));
		assertThrows(RuntimeException.class,
				() -> ShikigamiSelectPayload.STREAM_CODEC.decode(buffer),
				"the decode side must refuse an id past the cap, not truncate it");
	}

	@Test
	void stateSnapshotCarriesOneEntryPerRosterSlot() {
		int slots = MegumiShikigami.values().length;
		byte[] codes = new byte[slots];
		long[] readyAt = new long[slots];
		for (int i = 0; i < slots; i++) {
			// Distinct per slot: a transposed read lands on a different value and fails the compare.
			codes[i] = (byte) MegumiShikigamiSlotState.values()[i % MegumiShikigamiSlotState.values().length].ordinal();
			readyAt[i] = 1_000L + i * 37L;
		}
		ShikigamiStatePayload sent = new ShikigamiStatePayload("elephant", codes, readyAt, 4_242L);
		ShikigamiStatePayload received = roundTrip(ShikigamiStatePayload.STREAM_CODEC, sent);

		assertEquals("elephant", received.selectedId(), "the selected id must survive the wire");
		assertArrayEquals(codes, received.stateCodes(), "state codes must not be read out of the deadline array");
		assertArrayEquals(readyAt, received.cooldownUntilGameTimes(),
				"deadlines must not be read out of the state-code array");
		assertEquals(4_242L, received.gameTime(), "the snapshot's own game time must survive");
	}

	@Test
	void stateSnapshotRoundTripsEverySlotState() {
		// The codec is length-agnostic on purpose; this pins that every constant of the value space
		// survives a round trip, including the four the server cannot produce yet.
		MegumiShikigamiSlotState[] states = MegumiShikigamiSlotState.values();
		byte[] codes = new byte[states.length];
		long[] readyAt = new long[states.length];
		for (int i = 0; i < states.length; i++) {
			codes[i] = (byte) states[i].ordinal();
			readyAt[i] = 100L + i;
		}
		ShikigamiStatePayload received = roundTrip(ShikigamiStatePayload.STREAM_CODEC,
				new ShikigamiStatePayload("dogs", codes, readyAt, 7L));

		assertArrayEquals(codes, received.stateCodes());
		assertEquals(MegumiShikigamiSlotState.COOLDOWN.ordinal(), received.stateCodes()[2],
				"the ordinal is the wire value: reordering the enum is a wire break, not a rename");
	}

	@Test
	void anImplausibleSlotCountIsRefusedInsteadOfAllocated() {
		// A snapshot can only ever describe the roster. A count outside it is a corrupt buffer, and
		// trusting it would let a hostile length allocate an array of it.
		RegistryFriendlyByteBuf buffer = buffer();
		buffer.writeUtf("dogs", 16);
		buffer.writeVarInt(65_536);
		assertThrows(IllegalArgumentException.class,
				() -> ShikigamiStatePayload.STREAM_CODEC.decode(buffer),
				"a slot count no roster can produce must be refused");
	}

	@Test
	void aNegativeSlotCountIsRefusedInsteadOfAllocated() {
		// The other half of the same guard: a negative count would allocate a negative-length array and
		// crash the client, so it is refused with the same distrust as an oversized one.
		RegistryFriendlyByteBuf buffer = buffer();
		buffer.writeUtf("dogs", 16);
		buffer.writeVarInt(-1);
		assertThrows(IllegalArgumentException.class,
				() -> ShikigamiStatePayload.STREAM_CODEC.decode(buffer),
				"a negative slot count is a corrupt buffer, not an empty snapshot");
	}

	@Test
	void payloadTypeIdsAreStableWireIdentifiers() {
		assertEquals("jujutsumod:shikigami_select", ShikigamiSelectPayload.TYPE.id().toString(),
				"renaming a payload type silently breaks every client on the old id");
		assertEquals("jujutsumod:shikigami_state", ShikigamiStatePayload.TYPE.id().toString());
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
		RegistryFriendlyByteBuf buffer = buffer();
		codec.encode(buffer, payload);
		return buffer;
	}

	private static RegistryFriendlyByteBuf buffer() {
		return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
	}
}
