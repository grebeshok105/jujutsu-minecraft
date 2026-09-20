package jujutsu.mod.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

final class MegumiTonguePayloadCodecTest {
	private static final UUID OWNER = UUID.fromString("2ad7cb3f-c1a1-4a02-9b7b-df8b4d8d3f90");

	@Test
	void activeAnchoredPayloadRoundTripsEveryField() {
		MegumiTongueStatePayload sent = new MegumiTongueStatePayload(
				OWNER, true, MegumiTongueStatePayload.ANCHORED,
				12.25, 64.5, -3.75, 42_424L);
		MegumiTongueStatePayload received = roundTrip(sent);
		assertEquals(sent, received);
	}

	@Test
	void retractingPayloadKeepsAnchorAndShotTimeForClientAnimation() {
		MegumiTongueStatePayload sent = new MegumiTongueStatePayload(
				OWNER, true, MegumiTongueStatePayload.RETRACTING,
				1.0, 2.0, 3.0, 7_777L);
		MegumiTongueStatePayload received = roundTrip(sent);
		assertEquals(OWNER, received.ownerUuid());
		assertEquals(MegumiTongueStatePayload.RETRACTING, received.phase());
		assertEquals(1.0, received.anchorX());
		assertEquals(7_777L, received.shotGameTime());
	}

	@Test
	void hardTeardownCarriesOwnerAndInactiveFlag() {
		MegumiTongueStatePayload sent = new MegumiTongueStatePayload(
				OWNER, false, MegumiTongueStatePayload.RETRACTING,
				0.0, 0.0, 0.0, 99L);
		MegumiTongueStatePayload received = roundTrip(sent);
		assertEquals(OWNER, received.ownerUuid());
		assertEquals(false, received.active());
		assertEquals(MegumiTongueStatePayload.RETRACTING, received.phase());
	}

	private static MegumiTongueStatePayload roundTrip(MegumiTongueStatePayload payload) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			MegumiTongueStatePayload.STREAM_CODEC.encode(buffer, payload);
			MegumiTongueStatePayload decoded = MegumiTongueStatePayload.STREAM_CODEC.decode(buffer);
			assertEquals(0, buffer.readableBytes());
			return decoded;
		} finally {
			buffer.release();
		}
	}
}
