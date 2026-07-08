package jujutsu.mod.character;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraLoadout;
import jujutsu.mod.network.CharacterSelectionSyncPayload;

public final class CharacterSelectionManager {
	private static final Map<UUID, JujutsuCharacter> SELECTIONS = new ConcurrentHashMap<>();

	private CharacterSelectionManager() {}

	public static void select(ServerPlayer player, JujutsuCharacter character) {
		if (character == JujutsuCharacter.NONE) {
			SELECTIONS.remove(player.getUUID());
		} else {
			SELECTIONS.put(player.getUUID(), character);
		}
		if (character == JujutsuCharacter.NOBARA) {
			ProjectJjkNobaraLoadout.ensureStarterTools(player);
		}
		broadcast(player.getServer(), player.getUUID(), character);
	}

	public static void syncTo(ServerPlayer player) {
		for (Map.Entry<UUID, JujutsuCharacter> entry : SELECTIONS.entrySet()) {
			send(player, entry.getKey(), entry.getValue());
		}
	}

	public static void clear(ServerPlayer player) {
		JujutsuCharacter previous = SELECTIONS.remove(player.getUUID());
		if (previous != null) {
			broadcast(player.getServer(), player.getUUID(), JujutsuCharacter.NONE);
		}
	}

	private static void broadcast(MinecraftServer server, UUID playerId, JujutsuCharacter character) {
		if (server == null) {
			return;
		}
		for (ServerPlayer target : server.getPlayerList().getPlayers()) {
			send(target, playerId, character);
		}
	}

	private static void send(ServerPlayer target, UUID playerId, JujutsuCharacter character) {
		if (ServerPlayNetworking.canSend(target, CharacterSelectionSyncPayload.TYPE)) {
			ServerPlayNetworking.send(target, new CharacterSelectionSyncPayload(playerId, character.id(), character.modelId()));
		}
	}
}
