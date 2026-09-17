package jujutsu.mod.cursedincident.runtime;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentWorldSink;
import jujutsu.mod.cursedincident.infection.InfectionQueue;
import jujutsu.mod.cursedincident.infection.InfectionSink;

/** Bounded world work driver; logical age advances even while an incident's chunk is unloaded. */
public final class IncidentRuntime {
	private static final int PERIOD_TICKS = 20;
	private static IncidentWorldSink sink = IncidentWorldSink.NOOP;
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
		var records = IncidentControl.recordsForRuntime();
		int active = 0;
		for (IncidentRecord record : records) {
			if (isActive(levelFor(server, record), record)) {
				active++;
			}
		}
		int share = active <= 0 ? 0 : Math.max(1, 64 / active);
		for (IncidentRecord record : records) {
			if (record == null || record.scarred) {
				continue;
			}
			ServerLevel level = levelFor(server, record);
			if (level == null) {
				continue;
			}
			long now = level.getGameTime();
			IncidentControl.advanceTo(record, record.ageTicks(now));
			if (!isLoaded(level, record) || record.sealed) {
				continue;
			}
			sink.tickZone(level, record, share);
		}
		PerceptionOverrideRuntime.tick(server);
		flushPendingDrains();
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
				if (record == null || record.center == null || record.scarred || record.sealed
						|| record.dimension != null && record.dimension != level.dimension()) {
					continue;
				}
				if (isLoaded(level, record)) {
					sink.tickZone(level, record, 64);
				}
			}
		}
	}

	private static boolean isActive(ServerLevel level, IncidentRecord record) {
		return level != null && record != null && !record.scarred && !record.sealed
				&& (record.dimension == null || record.dimension == level.dimension());
	}

	private static ServerLevel levelFor(MinecraftServer server, IncidentRecord record) {
		return server.getLevel(record == null || record.dimension == null ? Level.OVERWORLD : record.dimension);
	}

	private static boolean isLoaded(ServerLevel level, IncidentRecord record) {
		return level != null && record != null && record.center != null
				&& level.getChunkSource().hasChunk(record.center.getX() >> 4, record.center.getZ() >> 4);
	}

	public static void clear() {
		sink = IncidentWorldSink.NOOP;
		InfectionSink.clearRuntimeState();
		InfectionQueue.clearRuntimeState();
		PerceptionOverrideRuntime.clear();
		PENDING_DRAIN.clear();
	}
}
