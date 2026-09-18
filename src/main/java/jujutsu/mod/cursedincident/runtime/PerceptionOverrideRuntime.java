package jujutsu.mod.cursedincident.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.IncidentRecord;
import jujutsu.mod.cursedincident.IncidentStage;
import jujutsu.mod.cursedincident.infection.ZoneGeometry;
import jujutsu.mod.network.IncidentPerceptionPayload;

/** O(1) per-player server perception override, refreshed at the same 20-tick cadence as incidents. */
public final class PerceptionOverrideRuntime {
	private static final Map<UUID, Boolean> OVERRIDES = new HashMap<>();
	private static boolean registered;

	private PerceptionOverrideRuntime() {
	}

	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.overworld().getGameTime() % 20L == 0L) {
				tick(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
	}

	public static void tick(MinecraftServer server) {
		Set<UUID> seen = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			seen.add(player.getUUID());
			boolean override = shouldOverride(player, IncidentControl.recordsForRuntime());
			Boolean old = OVERRIDES.put(player.getUUID(), override);
			if (old == null || old.booleanValue() != override) {
				send(player, override);
			}
		}
		OVERRIDES.keySet().removeIf(id -> !seen.contains(id));
	}

	public static boolean shouldOverride(Player player, Iterable<IncidentRecord> records) {
		if (player == null || records == null) {
			return false;
		}
		for (IncidentRecord record : records) {
			if (record == null || record.center == null || record.stage == null || record.scarred || record.sealed
					|| !record.stage.atLeast(IncidentStage.CRITICAL)) {
				continue;
			}
			if (record.dimension != null && player.level().dimension() != record.dimension) {
				continue;
			}
			if (ZoneGeometry.contains(ZoneGeometry.shapeOf(record.params), record.center, record.radius,
					player.blockPosition())) {
				return true;
			}
		}
		return false;
	}
	public static boolean shouldOverride(IncidentStage stage, boolean inside, boolean sealed, boolean scarred) {
		return !sealed && !scarred && inside && stage != null && stage.atLeast(IncidentStage.CRITICAL);
	}

	public static boolean isOverridden(ServerPlayer player) {
		return player != null && Boolean.TRUE.equals(OVERRIDES.get(player.getUUID()));
	}

	public static void clear() {
		OVERRIDES.clear();
	}

	public static Map<UUID, Boolean> snapshotForTest() {
		return Map.copyOf(OVERRIDES);
	}

	private static void send(ServerPlayer player, boolean override) {
		if (player.connection == null || !ServerPlayNetworking.canSend(player, IncidentPerceptionPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new IncidentPerceptionPayload(override));
	}
}
