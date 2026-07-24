package jujutsu.mod.character;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkNobaraLoadout;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.registry.JujutsuAttachments;

public final class CharacterSelectionManager {
	private CharacterSelectionManager() {}

	public static void select(ServerPlayer player, JujutsuCharacter character) {
		CharacterPlayerState current = state(player);
		boolean claimStarter = character == JujutsuCharacter.NOBARA && !current.hasClaimedStarter(character);
		CharacterPlayerState updated = current.withSelectedCharacter(character);
		if (claimStarter) {
			updated = updated.claimStarter(character);
		}
		attachments(player).setAttached(JujutsuAttachments.CHARACTER_STATE, updated);
		CharacterCombatModifiers.applyForSelection(player, character);
		if (claimStarter) {
			ProjectJjkNobaraLoadout.ensureStarterTools(player);
		}
		broadcast(player.getServer(), player.getUUID(), character);
	}

	public static JujutsuCharacter selected(ServerPlayer player) {
		return state(player).selectedCharacter();
	}

	public static void syncOnJoin(ServerPlayer joining) {
		MinecraftServer server = joining.getServer();
		if (server == null) {
			return;
		}
		JujutsuCharacter joiningCharacter = selected(joining);
		send(joining, joining.getUUID(), joiningCharacter);
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			if (online.getUUID().equals(joining.getUUID())) {
				continue;
			}
			send(joining, online.getUUID(), selected(online));
			send(online, joining.getUUID(), joiningCharacter);
		}
	}

	public static void disconnect(ServerPlayer player) {
		broadcast(player.getServer(), player.getUUID(), JujutsuCharacter.NONE);
	}

	private static CharacterPlayerState state(ServerPlayer player) {
		CharacterPlayerState state = attachments(player).getAttached(JujutsuAttachments.CHARACTER_STATE);
		return state == null ? CharacterPlayerState.DEFAULT : state;
	}

	private static AttachmentTarget attachments(ServerPlayer player) {
		return (AttachmentTarget) player;
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
