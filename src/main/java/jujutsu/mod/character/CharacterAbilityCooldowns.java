package jujutsu.mod.character;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-authoritative, short-lived cooldowns shared by character ability slots.
 *
 * <p>Keyed by vessel as well as by player and slot, because a slot is an input position and means a
 * different ability for every vessel. Keying on {@code (player, slot)} alone made one vessel's cooldown
 * refuse another's ability after a switch, and it disagreed with the client mirror, which has always
 * keyed on the vessel — so the client believed the ability was up while the server refused it.
 *
 * <p>The vessel is resolved here rather than passed in: every caller already casts as the selected
 * vessel, and asking them to say so again is a second place to get it wrong.
 */
public final class CharacterAbilityCooldowns {
	private static final Map<Key, Long> READY_AT = new HashMap<>();

	private CharacterAbilityCooldowns() {}

	public static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearAllForPlayer(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> READY_AT.clear());
	}

	/**
	 * Drops every cooldown entry for the player across all vessels. Exists for the dev control
	 * surface + gametests.
	 */
	public static void clearAllForPlayer(UUID playerId) {
		READY_AT.keySet().removeIf(key -> key.playerId().equals(playerId));
	}

	public static boolean isReady(ServerPlayer player, CharacterAbility ability) {
		return remainingTicks(player, ability) <= 0;
	}

	public static int remainingTicks(ServerPlayer player, CharacterAbility ability) {
		long readyAt = READY_AT.getOrDefault(keyFor(player, ability), 0L);
		return Math.max(0, (int) Math.min(Integer.MAX_VALUE, readyAt - player.level().getGameTime()));
	}

	public static void start(ServerPlayer player, CharacterAbility ability, int durationTicks) {
		if (durationTicks <= 0) {
			return;
		}
		READY_AT.put(keyFor(player, ability), player.level().getGameTime() + durationTicks);
	}

	/**
	 * Drops one entry. The caller must still be selected as the vessel that started it — the key resolves
	 * the vessel from the player, so clearing after a switch silently removes nothing. That rules out
	 * calling this from a selection change to wipe the old vessel's cooldowns; it would not work, and it
	 * would be the wrong thing anyway, since a cooldown resumes where it left off on switching back.
	 */
	public static void clear(ServerPlayer player, CharacterAbility ability) {
		READY_AT.remove(keyFor(player, ability));
	}

	private static Key keyFor(ServerPlayer player, CharacterAbility ability) {
		return new Key(player.getUUID(), CharacterSelectionManager.selected(player), ability);
	}

	private record Key(UUID playerId, JujutsuCharacter character, CharacterAbility ability) {}
}
