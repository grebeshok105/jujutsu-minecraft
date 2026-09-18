package jujutsu.mod.cursedincident.runtime;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentControl.WorkCenter;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentWorldSink;
import jujutsu.mod.cursedincident.infection.InfectionQueue;
import jujutsu.mod.cursedincident.infection.InfectionSink;
/** Bounded world work driver; logical age advances even while an incident's chunk is unloaded. */
public final class IncidentRuntime {
	private static final int PERIOD_TICKS = 20;
	private static final int MAX_PENDING_DELTAS_PER_TICK = 8;
	private static IncidentWorldSink sink = IncidentWorldSink.NOOP;
	private static MinecraftServer activeServer;
	private static boolean registered;

	private IncidentRuntime() {
	}

	public static void bindWorldSink(IncidentWorldSink value) {
		sink = value == null ? IncidentWorldSink.NOOP : value;
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.overworld().getGameTime() % PERIOD_TICKS == 0L) {
				tick(server);
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> drainLoaded(level));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	public static void tick(MinecraftServer server) {
		if (server == null || server.overworld() == null) {
			return;
		}
		activeServer = server;
		List<IncidentRecord> records = IncidentControl.recordsForRuntime();
		int loadedUnits = loadedWorkUnits(records);
		int share = 64 / Math.max(1, loadedUnits);
		for (IncidentRecord record : records) {
			if (record == null || record.sealed) {
				continue;
			}
			ServerLevel level = levelFor(server, record);
			if (level == null) {
				continue;
			}
			if (!record.scarred) {
				long now = level.getGameTime();
				IncidentControl.advanceTo(record, record.ageTicks(now));
			}
			for (WorkCenter workCenter : IncidentControl.workCenters(record)) {
				if (workCenter.isParent() && record.scarred) {
					continue;
				}
				if (!isLoaded(level, workCenter.center())) {
					continue;
				}
				if (workCenter.isParent()) {
					replayPendingDeltas(level, record);
				}
				sink.tickZone(level, record, workCenter.center(), workCenter.nodeId(), share);
			}
		}
		PerceptionOverrideRuntime.tick(server);
		flushPendingDrains();
	}

	/** Counts loaded work centres, including self-sustaining nodes on scarred parents. */
	public static int loadedWorkUnits(List<IncidentRecord> records) {
		if (activeServer == null || records == null) {
			return 0;
		}
		int loaded = 0;
		for (IncidentRecord record : records) {
			if (record == null || record.sealed) {
				continue;
			}
			ServerLevel level = levelFor(activeServer, record);
			for (WorkCenter workCenter : IncidentControl.workCenters(record)) {
				if (workCenter.isParent() && record.scarred) {
					continue;
				}
				if (isLoaded(level, workCenter.center())) {
					loaded++;
				}
			}
		}
		return loaded;
	}

	private static void drainLoaded(ServerLevel level) {
		// Never mutate the world inside CHUNK_LOAD — a setBlock there re-enters the
		// chunk pipeline ("Recursive update" crash). Defer to the next server tick.
		PENDING_DRAIN.add(level);
	}

	private static final java.util.Set<ServerLevel> PENDING_DRAIN =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static void flushPendingDrains() {
		for (ServerLevel level : PENDING_DRAIN) {
			PENDING_DRAIN.remove(level);
			for (IncidentRecord record : IncidentControl.recordsForRuntime()) {
				if (record == null || record.sealed
						|| record.dimension != null && record.dimension != level.dimension()) {
					continue;
				}
				for (WorkCenter workCenter : IncidentControl.workCenters(record)) {
					if (workCenter.isParent() && record.scarred
							|| !isLoaded(level, workCenter.center())) {
						continue;
					}
					if (workCenter.isParent()) {
						replayPendingDeltas(level, record);
					}
					sink.tickZone(level, record, workCenter.center(), workCenter.nodeId(), 64);
				}
			}
		}
	}

	private static void replayPendingDeltas(ServerLevel level, IncidentRecord record) {
		int replayed = 0;
		while (replayed++ < MAX_PENDING_DELTAS_PER_TICK && !record.pendingDeltas.isEmpty()) {
			IncidentRecord.PendingDelta delta = record.pendingDeltas.remove(0);
			// Replay the committed pair directly; never re-run transitionsBetween.
			record.stage = delta.to();
			sink.applyStageDelta(level, record, delta.from(), delta.to());
		}
	}

	private static boolean isActive(ServerLevel level, IncidentRecord record) {
		return level != null && record != null && !record.sealed
				&& (record.dimension == null || record.dimension == level.dimension());
	}

	private static ServerLevel levelFor(MinecraftServer server, IncidentRecord record) {
		return server.getLevel(record == null || record.dimension == null ? Level.OVERWORLD : record.dimension);
	}

	private static boolean isLoaded(ServerLevel level, BlockPos center) {
		return level != null && center != null
				&& level.getChunkSource().hasChunk(center.getX() >> 4, center.getZ() >> 4);
	}

	public static void clear() {
		sink = IncidentWorldSink.NOOP;
		activeServer = null;
		InfectionSink.clearRuntimeState();
		InfectionQueue.clearRuntimeState();
		PerceptionOverrideRuntime.clear();
		PENDING_DRAIN.clear();
	}
}
