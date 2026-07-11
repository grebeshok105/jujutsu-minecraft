package jujutsu.mod.character.nobara.projectjjk;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import jujutsu.mod.network.ResonantMomentumPayload;

/** Server-owned, non-stacking duration state granted by a successful Straw Doll Resonance. */
public final class ResonantMomentum {
	public static final ResonantMomentum GLOBAL = new ResonantMomentum(
			ProjectJjkNobaraProfile.RESONANT_MOMENTUM_DURATION_TICKS,
			ProjectJjkNobaraProfile.RESONANT_MOMENTUM_MULTIPLIER);

	private final int durationTicks;
	private final float multiplier;
	private final Map<UUID, Long> expiresAt = new HashMap<>();

	public ResonantMomentum(int durationTicks, float multiplier) {
		if (durationTicks <= 0 || multiplier < 1.0f) throw new IllegalArgumentException("Invalid Momentum tuning");
		this.durationTicks = durationTicks;
		this.multiplier = multiplier;
	}

	public long grant(UUID playerId, long gameTime) {
		long expiry = gameTime + durationTicks;
		expiresAt.put(playerId, expiry);
		return expiry;
	}

	public boolean isActive(UUID playerId, long gameTime) {
		return remainingTicks(playerId, gameTime) > 0;
	}

	public int remainingTicks(UUID playerId, long gameTime) {
		Long expiry = expiresAt.get(playerId);
		if (expiry == null) return 0;
		long remaining = expiry - gameTime;
		if (remaining <= 0) {
			expiresAt.remove(playerId);
			return 0;
		}
		return (int)Math.min(Integer.MAX_VALUE, remaining);
	}

	public float damageMultiplier(UUID playerId, long gameTime) {
		return isActive(playerId, gameTime) ? multiplier : 1.0f;
	}

	public void clear(UUID playerId) { expiresAt.remove(playerId); }
	public void clearAll() { expiresAt.clear(); }

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GLOBAL::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> GLOBAL.clear(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> GLOBAL.clearAll());
	}

	public static void grant(ServerPlayer player) {
		GLOBAL.grant(player.getUUID(), player.level().getGameTime());
		sync(player);
	}

	public static void sync(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ResonantMomentumPayload.TYPE)) {
			ServerPlayNetworking.send(player, new ResonantMomentumPayload(
					GLOBAL.remainingTicks(player.getUUID(), player.level().getGameTime())));
		}
	}

	private void tick(MinecraftServer server) {
		long gameTime = server.overworld().getGameTime();
		for (UUID playerId : java.util.List.copyOf(expiresAt.keySet())) {
			if (remainingTicks(playerId, gameTime) > 0) continue;
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			if (player != null) sync(player);
		}
	}

	public static float damageMultiplier(ServerPlayer player) {
		return player == null ? 1.0f : GLOBAL.damageMultiplier(player.getUUID(), player.level().getGameTime());
	}

	public static int scaleTicks(int baseTicks, float speedMultiplier) {
		if (baseTicks <= 0) return 0;
		return Math.max(1, Math.round(baseTicks / Math.max(1.0f, speedMultiplier)));
	}

	public static int scaleTicks(ServerPlayer player, int baseTicks) {
		return scaleTicks(baseTicks, damageMultiplier(player));
	}

	public static int accelerateElapsedTicks(ServerPlayer player, int elapsedTicks) {
		return Math.max(0, (int)Math.floor(elapsedTicks * damageMultiplier(player)));
	}
}
