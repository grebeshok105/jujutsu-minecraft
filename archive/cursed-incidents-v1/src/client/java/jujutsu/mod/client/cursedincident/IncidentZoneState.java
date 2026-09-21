package jujutsu.mod.client.cursedincident;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.phys.Vec3;
import jujutsu.mod.network.IncidentZoneStatePayload;

/**
 * Client mirror of incident work centers, keyed by {@code (dimension, incidentId, nodeId)}.
 * A parent's {@code active=false} removes exactly that one key — secondary nodes are
 * independent entries and die only by their own deactivation (R20 child lifecycle).
 */
public final class IncidentZoneState {

	public record Key(String dimension, UUID incidentId, UUID nodeId) {
	}

	/** One rendered work center. */
	public static final class Zone {
		public final Key key;
		public Vec3 center;
		public double radius;
		public int stage;
		public String atmosphereId = "";
		public int sealTier;
		public int sealIntegrity;
		public long lastSeenClientTick;

		Zone(Key key) {
			this.key = key;
		}

		/** Seal degradation band 0..4, same quarter math as the server's degradationBand. */
		public int sealBand() {
			if (sealTier <= 0) {
				return -1; // unsealed
			}
			int maximum = switch (Math.max(1, Math.min(3, sealTier))) {
				case 1 -> 100;
				case 2 -> 200;
				default -> 400;
			};
			int clamped = Math.max(0, Math.min(maximum, sealIntegrity));
			if (clamped * 4 >= maximum * 3) {
				return 0;
			}
			if (clamped * 2 >= maximum) {
				return 1;
			}
			if (clamped * 4 >= maximum) {
				return 2;
			}
			return clamped == 0 ? 4 : 3;
		}
	}

	private static final Map<Key, Zone> ZONES = new HashMap<>();
	private static long clientTick;

	private IncidentZoneState() {
	}

	/** Client ticks without a heartbeat before a zone is presumed dead (3× server resend). */
	private static final long STALE_AFTER_TICKS = 300L;

	public static void tick() {
		clientTick++;
		// The server resends every live zone on a slow heartbeat; anything unheard past the
		// TTL is dead — its teardown packet either never reached this client (walked out of
		// the delivery radius) or the zone stopped existing while we were elsewhere.
		ZONES.values().removeIf(zone -> clientTick - zone.lastSeenClientTick > STALE_AFTER_TICKS);
	}

	public static void apply(IncidentZoneStatePayload payload) {
		if (payload == null || payload.incidentId() == null) {
			return;
		}
		Key key = new Key(payload.dimension(), payload.incidentId(), payload.nodeId());
		if (!payload.active()) {
			// Exactly one key dies — never siblings/children of the same incident.
			ZONES.remove(key);
			return;
		}
		// A malformed or corrupt snapshot must never reach render math: NaN bypasses the
		// LOD distance check and feeds NaN vertices into the shared buffer.
		if (!Double.isFinite(payload.centerX()) || !Double.isFinite(payload.centerY())
				|| !Double.isFinite(payload.centerZ()) || !Double.isFinite(payload.radius())
				|| payload.radius() < 0.0) {
			return;
		}
		Zone zone = ZONES.computeIfAbsent(key, Zone::new);
		zone.center = new Vec3(payload.centerX(), payload.centerY(), payload.centerZ());
		zone.radius = payload.radius();
		zone.stage = payload.stage();
		zone.atmosphereId = payload.atmosphereId();
		zone.sealTier = payload.sealTier();
		zone.sealIntegrity = payload.sealIntegrity();
		zone.lastSeenClientTick = clientTick;
	}

	public static Iterable<Zone> zones() {
		return ZONES.values();
	}

	public static int size() {
		return ZONES.size();
	}

	public static void clear() {
		ZONES.clear();
	}
}
