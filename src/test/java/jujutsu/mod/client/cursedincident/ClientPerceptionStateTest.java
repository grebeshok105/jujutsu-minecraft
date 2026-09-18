package jujutsu.mod.client.cursedincident;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import jujutsu.mod.network.IncidentPerceptionPayload;

/** R74 client-half wire-state oracle: payload apply and disconnect clear. */
final class ClientPerceptionStateTest {
	@AfterEach
	void clear() {
		ClientPerceptionState.clear();
	}

	@Test
	void payloadSetsCriticalZoneBit() {
		ClientPerceptionState.apply(new IncidentPerceptionPayload(true));
		assertTrue(ClientPerceptionState.inCriticalZone());
		ClientPerceptionState.apply(new IncidentPerceptionPayload(false));
		assertFalse(ClientPerceptionState.inCriticalZone());
	}

	@Test
	void disconnectClearRemovesStaleVisibility() {
		ClientPerceptionState.apply(new IncidentPerceptionPayload(true));
		ClientPerceptionState.clear();
		assertFalse(ClientPerceptionState.inCriticalZone());
	}
}
