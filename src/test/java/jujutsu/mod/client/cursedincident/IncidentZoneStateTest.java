package jujutsu.mod.client.cursedincident;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jujutsu.mod.network.IncidentZoneStatePayload;

/**
 * Client store lifecycle: keyed cleanup and the R20 child-survival rule — a parent's
 * active=false must never clear sibling/child node entries.
 */
final class IncidentZoneStateTest {

	@BeforeEach
	void reset() {
		IncidentZoneState.clear();
	}

	private static IncidentZoneStatePayload zone(UUID incident, UUID node, boolean active) {
		return new IncidentZoneStatePayload("minecraft:overworld", incident, node,
				1.0, 64.0, 1.0, 10.0, 1, "blight", 0, 0, active);
	}

	@Test
	void applyRegistersAndDeactivatesExactlyOneKey() {
		UUID incident = UUID.randomUUID();
		IncidentZoneState.apply(zone(incident, IncidentZoneStatePayload.PARENT_NODE, true));
		assertEquals(1, IncidentZoneState.size());
		IncidentZoneState.apply(zone(incident, IncidentZoneStatePayload.PARENT_NODE, false));
		assertEquals(0, IncidentZoneState.size());
	}

	@Test
	void parentCleanupLeavesChildNodeAlive() {
		UUID incident = UUID.randomUUID();
		UUID child = UUID.randomUUID();
		IncidentZoneState.apply(zone(incident, IncidentZoneStatePayload.PARENT_NODE, true));
		IncidentZoneState.apply(zone(incident, child, true));
		assertEquals(2, IncidentZoneState.size());
		// Parent dies — the child entry must survive (R20).
		IncidentZoneState.apply(zone(incident, IncidentZoneStatePayload.PARENT_NODE, false));
		assertEquals(1, IncidentZoneState.size());
		IncidentZoneState.Zone survivor = IncidentZoneState.zones().iterator().next();
		assertEquals(child, survivor.key.nodeId());
		// Child dies only by its own deactivation.
		IncidentZoneState.apply(zone(incident, child, false));
		assertEquals(0, IncidentZoneState.size());
	}

	@Test
	void sealBandFollowsQuarterMath() {
		UUID incident = UUID.randomUUID();
		IncidentZoneState.apply(new IncidentZoneStatePayload("minecraft:overworld", incident,
				IncidentZoneStatePayload.PARENT_NODE, 0, 0, 0, 8.0, 0, "", 2, 200, true));
		IncidentZoneState.Zone zone = IncidentZoneState.zones().iterator().next();
		assertEquals(0, zone.sealBand());
		IncidentZoneState.apply(new IncidentZoneStatePayload("minecraft:overworld", incident,
				IncidentZoneStatePayload.PARENT_NODE, 0, 0, 0, 8.0, 0, "", 2, 100, true));
		assertEquals(1, zone.sealBand());
		IncidentZoneState.apply(new IncidentZoneStatePayload("minecraft:overworld", incident,
				IncidentZoneStatePayload.PARENT_NODE, 0, 0, 0, 8.0, 0, "", 2, 60, true));
		assertEquals(2, zone.sealBand());
		IncidentZoneState.apply(new IncidentZoneStatePayload("minecraft:overworld", incident,
				IncidentZoneStatePayload.PARENT_NODE, 0, 0, 0, 8.0, 0, "", 2, 20, true));
		assertEquals(3, zone.sealBand());
		IncidentZoneState.apply(new IncidentZoneStatePayload("minecraft:overworld", incident,
				IncidentZoneStatePayload.PARENT_NODE, 0, 0, 0, 8.0, 0, "", 2, 0, true));
		assertEquals(4, zone.sealBand());
	}

	@Test
	void unsealedZoneReportsNoBand() {
		UUID incident = UUID.randomUUID();
		IncidentZoneState.apply(zone(incident, IncidentZoneStatePayload.PARENT_NODE, true));
		assertEquals(-1, IncidentZoneState.zones().iterator().next().sealBand());
	}
}
