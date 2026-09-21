package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import java.util.UUID;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import jujutsu.mod.network.IncidentZoneStatePayload;
/** Codec + store-lifecycle contract for the incident zone-state channel (C5). */
final class IncidentZoneStatePayloadTest {

	@Test
	void payloadCarriesFullZoneSnapshotAndRoundTripsThroughStreamCodec() {
		UUID incident = UUID.randomUUID();
		UUID node = UUID.randomUUID();
		IncidentZoneStatePayload sent = new IncidentZoneStatePayload(
				"minecraft:overworld", incident, node, 10.5, 64.5, -3.5, 12.0,
				2, "blight", 2, 140, true);
		IncidentZoneStatePayload payload = roundTrip(sent);
		assertEquals(sent.dimension(), payload.dimension());
		assertEquals(sent.incidentId(), payload.incidentId());
		assertEquals(sent.nodeId(), payload.nodeId());
		assertEquals(sent.centerX(), payload.centerX());
		assertEquals(sent.centerY(), payload.centerY());
		assertEquals(sent.centerZ(), payload.centerZ());
		assertEquals(sent.radius(), payload.radius());
		assertEquals(sent.stage(), payload.stage());
		assertEquals(sent.atmosphereId(), payload.atmosphereId());
		assertEquals(sent.sealTier(), payload.sealTier());
		assertEquals(sent.sealIntegrity(), payload.sealIntegrity());
		assertEquals(sent.active(), payload.active());
	}

	private static IncidentZoneStatePayload roundTrip(IncidentZoneStatePayload payload) {
		RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
				Unpooled.buffer(), RegistryAccess.EMPTY);
		try {
			IncidentZoneStatePayload.STREAM_CODEC.encode(buffer, payload);
			IncidentZoneStatePayload decoded = IncidentZoneStatePayload.STREAM_CODEC.decode(buffer);
			assertEquals(0, buffer.readableBytes());
			return decoded;
		} finally {
			buffer.release();
		}
	}

	@Test
	void nullNodeFallsBackToParentKey() {
		IncidentZoneStatePayload payload = new IncidentZoneStatePayload(
				"minecraft:overworld", UUID.randomUUID(), null, 0, 0, 0, 8.0,
				0, "", 0, 0, true);
		assertEquals(IncidentZoneStatePayload.PARENT_NODE, payload.nodeId());
	}

	@Test
	void nullDimensionAndAtmosphereBecomeEmptyStrings() {
		IncidentZoneStatePayload payload = new IncidentZoneStatePayload(
				null, UUID.randomUUID(), null, 0, 0, 0, 8.0, 0, null, 0, 0, false);
		assertEquals("", payload.dimension());
		assertEquals("", payload.atmosphereId());
	}
}
