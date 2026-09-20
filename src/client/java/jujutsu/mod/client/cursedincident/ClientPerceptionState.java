package jujutsu.mod.client.cursedincident;

import jujutsu.mod.network.IncidentPerceptionPayload;

/** Client mirror of the server's O(1) critical-zone perception bit. */
public final class ClientPerceptionState {
	private static volatile boolean inCriticalZone;

	private ClientPerceptionState() {
	}

	public static boolean inCriticalZone() {
		return inCriticalZone;
	}

	public static void apply(IncidentPerceptionPayload payload) {
		boolean now = payload != null && payload.inCriticalZone();
		if (inCriticalZone && !now) {
			// The override is what lets a non-mage client hold zone state at all: when it
			// drops, the server stops sending zone packets entirely, so every cached zone
			// is stale the same tick — waiting out the TTL would keep rendering zones the
			// client is no longer allowed to see.
			IncidentZoneState.clear();
			IncidentZoneRenderer.clearCache();
		}
		inCriticalZone = now;
	}

	public static void clear() {
		inCriticalZone = false;
	}
}
