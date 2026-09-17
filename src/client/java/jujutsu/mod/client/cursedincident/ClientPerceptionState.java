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
		inCriticalZone = payload != null && payload.inCriticalZone();
	}

	public static void clear() {
		inCriticalZone = false;
	}
}
