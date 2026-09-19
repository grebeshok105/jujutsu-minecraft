package jujutsu.mod.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import jujutsu.mod.network.IncidentZoneStatePayload;

/** Codec + store-lifecycle contract for the incident zone-state channel (C5). */
final class IncidentZoneStatePayloadTest {

	@Test
	void payloadCarriesFullZoneSnapshot() {
		UUID incident = UUID.randomUUID();
		UUID node = UUID.randomUUID();
		IncidentZoneStatePayload payload = new IncidentZoneStatePayload(
				"minecraft:overworld", incident, node, 10.5, 64.5, -3.5, 12.0,
				2, "blight", 2, 140, true);
		assertEquals("minecraft:overworld", payload.dimension());
		assertEquals(incident, payload.incidentId());
		assertEquals(node, payload.nodeId());
		assertEquals(10.5, payload.centerX());
		assertEquals(2, payload.stage());
		assertEquals("blight", payload.atmosphereId());
		assertEquals(2, payload.sealTier());
		assertEquals(140, payload.sealIntegrity());
		assertTrue(payload.active());
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
