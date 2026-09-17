package jujutsu.mod.character;

import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuAttachments;

public final class CharacterSelectionManager {
	private CharacterSelectionManager() {}

	public static void select(ServerPlayer player, JujutsuCharacter character) {
		CharacterPlayerState current = state(player);
		JujutsuCharacter previous = current.selectedCharacter();
		CharacterDefinition arriving = JujutsuCharacters.definition(character);
		// The old vessel packs up before the new one is stored, so its hook still sees itself selected.
		// Every switch runs it, including re-selecting the same vessel, which is what the unconditional
		// Todo cleanup this replaced did.
		JujutsuCharacters.definition(previous).onDeselected(player, character);
		// A real vessel change is a clean slate (issue #84): the deadlines belonged to the vessel that
		// is leaving, so they go with it. Two deliberate details — this runs AFTER onDeselected (whose
		// teardown may arm one) and BEFORE the new selection is stored, while both the store and the
		// cooldown packet still resolve the outgoing vessel; and re-confirming the SAME vessel does not
		// clear anything, so the menu cannot be used as a cooldown reset.
		if (previous != character) {
			CharacterAbilityCooldowns.clearForCharacter(player.getUUID(), previous);
			for (CharacterAbility ability : CharacterAbility.values()) {
				JujutsuNetworking.sendAbilityCooldown(player, ability, 0);
			}
		}
		// Recorded for every vessel, not just the ones that hand something out: "has been this vessel at
		// least once" is a fact about the player, and claimStarter is idempotent.
		CharacterPlayerState updated = current.withSelectedCharacter(character).claimStarter(character);
		attachments(player).setAttached(JujutsuAttachments.CHARACTER_STATE, updated);
		CharacterCombatModifiers.applyForSelection(player, character);
		arriving.onSelected(player);
		player.refreshDimensions();
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
