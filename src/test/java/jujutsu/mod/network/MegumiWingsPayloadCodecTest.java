package jujutsu.mod.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class MegumiWingsPayloadCodecTest {
	private static final UUID OWNER = UUID.fromString("2ad7cb3f-c1a1-4a02-9b7b-df8b4d8d3f90");

	@Test
	void activePayloadRoundTripsOwnerPhaseAndStartTime() {
		MegumiWingsStatePayload sent = new MegumiWingsStatePayload(
				OWNER, true, MegumiWingsStatePayload.FLYING, 42_424L);
		MegumiWingsStatePayload received = roundTrip(sent);
		assertEquals(sent, received);
	}

	@Test
	void inactivePayloadRoundTripsForTeardown() {
		MegumiWingsStatePayload sent = new MegumiWingsStatePayload(
				OWNER, false, MegumiWingsStatePayload.FOLDING, 7_777L);
		MegumiWingsStatePayload received = roundTrip(sent);
		assertEquals(OWNER, received.ownerUuid());
		assertEquals(false, received.active());
		assertEquals(MegumiWingsStatePayload.FOLDING, received.phase());
		assertEquals(7_777L, received.phaseStartGameTime());
	}

	private static MegumiWingsStatePayload roundTrip(MegumiWingsStatePayload payload) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			MegumiWingsStatePayload.STREAM_CODEC.encode(buffer, payload);
			MegumiWingsStatePayload decoded = MegumiWingsStatePayload.STREAM_CODEC.decode(buffer);
			assertEquals(0, buffer.readableBytes());
			return decoded;
		}
		finally {
			buffer.release();
		}
	}
}
