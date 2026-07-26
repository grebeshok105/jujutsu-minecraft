package jujutsu.mod.character;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Server-authoritative, short-lived cooldowns shared by character ability slots. */
public final class CharacterAbilityCooldowns {
	private static final Map<Key, Long> READY_AT = new HashMap<>();

	private CharacterAbilityCooldowns() {}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY_AT.keySet().removeIf(key -> key.playerId().equals(handler.player.getUUID())));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> READY_AT.clear());
	}

	public static boolean isReady(ServerPlayer player, CharacterAbility ability) {
		return remainingTicks(player, ability) <= 0;
	}

	public static int remainingTicks(ServerPlayer player, CharacterAbility ability) {
		long readyAt = READY_AT.getOrDefault(new Key(player.getUUID(), ability), 0L);
		return Math.max(0, (int) Math.min(Integer.MAX_VALUE, readyAt - player.level().getGameTime()));
	}

	public static void start(ServerPlayer player, CharacterAbility ability, int durationTicks) {
		if (durationTicks <= 0) {
			return;
		}
		READY_AT.put(new Key(player.getUUID(), ability), player.level().getGameTime() + durationTicks);
	}

	public static void clear(ServerPlayer player, CharacterAbility ability) {
		READY_AT.remove(new Key(player.getUUID(), ability));
	}

	private record Key(UUID playerId, CharacterAbility ability) {}
}
