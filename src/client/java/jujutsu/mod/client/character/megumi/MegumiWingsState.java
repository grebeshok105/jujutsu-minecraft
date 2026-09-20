package jujutsu.mod.client.character.megumi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import jujutsu.mod.network.MegumiWingsStatePayload;

/** Client-side presentation cache for server-authoritative Nue wing phases. */
public final class MegumiWingsState {
	public enum Phase {
		MATERIALIZING(MegumiWingsStatePayload.MATERIALIZING, "animation.megumi_nue_wings.materialize", 8),
		GROUND_FOLDED(MegumiWingsStatePayload.GROUND_FOLDED, "animation.megumi_nue_wings.folded_idle", 1),
		FLYING(MegumiWingsStatePayload.FLYING, "animation.megumi_nue_wings.fly", 1),
		FOLDING(MegumiWingsStatePayload.FOLDING, "animation.megumi_nue_wings.fold", 6),
		UNFOLDING(MegumiWingsStatePayload.UNFOLDING, "animation.megumi_nue_wings.unfold", 6),
		DISSOLVING(MegumiWingsStatePayload.DISSOLVING, "animation.megumi_nue_wings.dissolve", 5);

		private final int wireValue;
		private final String clipId;
		private final int durationTicks;

		Phase(int wireValue, String clipId, int durationTicks) {
			this.wireValue = wireValue;
			this.clipId = clipId;
			this.durationTicks = durationTicks;
		}

		public int wireValue() {
			return wireValue;
		}

		public String clipId() {
			return clipId;
		}

		public int durationTicks() {
			return durationTicks;
		}

		public static Phase fromWire(int wireValue) {
			for (Phase phase : values()) {
				if (phase.wireValue == wireValue) {
					return phase;
				}
			}
			return null;
		}
	}

	private static final long STALE_AFTER_TICKS = 60L;
	private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
	private static long lastKnownGameTime;

	private MegumiWingsState() {}

	/** Applies a server snapshot; unknown phases are ignored rather than rendered speculatively. */
	public static void apply(MegumiWingsStatePayload payload) {
		if (payload == null) {
			return;
		}
		Phase phase = Phase.fromWire(payload.phase());
		if (phase == null) {
			return;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			Entry previous = ENTRIES.get(payload.ownerUuid());
			if (!payload.active()) {
				if (previous == null) {
					// A teardown for a manifestation this client never saw must not invent one:
					// the orphan-marker reconcile sends inactive to every joining player, and
					// creating a FOLDING entry here would flash ghost wings on every join.
					return;
				}
				long foldingStartedAt = !previous.active()
						? previous.localPhaseStartGameTime()
						: now;
				ENTRIES.put(payload.ownerUuid(), new Entry(
						// The wire phase is the teardown clip — FOLDING on the ground,
						// DISSOLVING in the air — not a hardcoded fold.
						phase,
						false,
						payload.phaseStartGameTime(),
						foldingStartedAt,
						now));
				return;
			}
			long localStart = previous != null && previous.active() && previous.phase() == phase
					? previous.localPhaseStartGameTime()
					: now;
			ENTRIES.put(payload.ownerUuid(), new Entry(
						phase,
						true,
						payload.phaseStartGameTime(),
						localStart,
						now));
		}
	}

	/** Returns the current phase, or {@code null} after teardown or stale-heartbeat cleanup. */
	public static Phase phaseFor(UUID ownerUuid) {
		if (ownerUuid == null) {
			return null;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
			Entry entry = ENTRIES.get(ownerUuid);
			return entry == null ? null : entry.phase();
		}
	}

	/** True while a player has a visible manifestation, including the short folding teardown. */
	public static boolean active(UUID ownerUuid) {
		return phaseFor(ownerUuid) != null;
	}

	/** Returns the normalized progress of the current phase in [0, 1]. */
	public static float phaseProgress(UUID ownerUuid, float partialTick) {
		if (ownerUuid == null) {
			return 0.0f;
		}
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
			Entry entry = ENTRIES.get(ownerUuid);
			if (entry == null) {
				return 0.0f;
			}
			if (entry.phase() == Phase.GROUND_FOLDED || entry.phase() == Phase.FLYING) {
				return 1.0f;
			}
			long start = entry.active() ? entry.phaseStartGameTime() : entry.localPhaseStartGameTime();
			float elapsed = (float) (now - start) + partialTick;
			return Math.max(0.0f, Math.min(1.0f, elapsed / entry.phase().durationTicks()));
		}
	}

	/** Maps a wire phase to the GeckoLib clip used by the wings controller. */
	public static String clipFor(int wirePhase) {
		Phase phase = Phase.fromWire(wirePhase);
		return phase == null ? null : phase.clipId();
	}

	/** Runs stale-heartbeat and completed-fold cleanup from the client tick hook. */
	public static void tick() {
		long now = gameTime();
		synchronized (ENTRIES) {
			cleanup(now);
		}
	}

	/** Drops all remote presentation state when the client disconnects or changes level. */
	public static void clear() {
		synchronized (ENTRIES) {
			ENTRIES.clear();
		}
	}

	private static void cleanup(long now) {
		Iterator<Map.Entry<UUID, Entry>> iterator = ENTRIES.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry entry = iterator.next().getValue();
			if (now - entry.lastSeenGameTime() > STALE_AFTER_TICKS
					|| (!entry.active()
							&& now - entry.localPhaseStartGameTime() >= entry.phase().durationTicks())) {
				iterator.remove();
			}
		}
	}

	private static long gameTime() {
		Minecraft client = Minecraft.getInstance();
		if (client.level != null) {
			lastKnownGameTime = client.level.getGameTime();
		}
		return lastKnownGameTime;
	}

	private record Entry(
			Phase phase,
			boolean active,
			long phaseStartGameTime,
			long localPhaseStartGameTime,
			long lastSeenGameTime) {}
}
