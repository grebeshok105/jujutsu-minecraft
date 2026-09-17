package jujutsu.mod.cursedincident.runtime;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import jujutsu.mod.cursedincident.IncidentControl;
import jujutsu.mod.cursedincident.policy.PressurePolicy;

/**
 * Natural incident spawning via accumulated cursed pressure (issue #110 spec §4.2).
 *
 * <p>Every {@value #TICK_PERIOD} ticks the world's pressure grows by
 * {@link PressurePolicy#accumulate}; a seeded roll against
 * {@link PressurePolicy#shouldSpawn} may then birth an incident 96–192 blocks off a
 * random online player. Pressure persists inside {@code IncidentSavedData} — this class
 * reaches it only through {@link IncidentControl}'s pressure API, never the store.
 */
public final class PressureRuntime {

	private static final long TICK_PERIOD = 2400; // 2 minutes
	private static final int MIN_OFFSET = 96;
	private static final int MAX_OFFSET = 192;

	private static long lastTickGameTime = -1;

	private PressureRuntime() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(PressureRuntime::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> lastTickGameTime = -1);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		if (overworld == null) {
			return;
		}
		long now = overworld.getGameTime();
		if (lastTickGameTime < 0) {
			lastTickGameTime = now;
			return;
		}
		long elapsed = now - lastTickGameTime;
		if (elapsed < TICK_PERIOD) {
			return;
		}
		lastTickGameTime = now;

		long pressure = IncidentControl.addPressure(PressurePolicy.accumulate(elapsed));
		int active = IncidentControl.list().size();
		if (!PressurePolicy.shouldSpawn(
				net.minecraft.util.RandomSource.create(overworld.getRandom().nextLong()),
				pressure, active)) {
			return;
		}
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer anchor = players.get(ThreadLocalRandom.current().nextInt(players.size()));
		double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
		int dist = MIN_OFFSET + ThreadLocalRandom.current().nextInt(MAX_OFFSET - MIN_OFFSET);
		BlockPos center = anchor.blockPosition().offset(
				(int) (Math.cos(angle) * dist), 0, (int) (Math.sin(angle) * dist));
		IncidentControl.spawn(new IncidentControl.SpawnRequest(
				center, anchor.level().dimension(), null, null,
				overworld.getRandom().nextLong(), null, null, null, null));
		IncidentControl.resetPressure();
	}
}
