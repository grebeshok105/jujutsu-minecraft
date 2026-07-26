package jujutsu.mod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.CharacterAbility;
import jujutsu.mod.character.CharacterAbilityExecutor;
import jujutsu.mod.character.CharacterSelectionManager;
import jujutsu.mod.character.JujutsuCharacter;
import jujutsu.mod.vfx.VfxCue;

public final class JujutsuNetworking {
	private JujutsuNetworking() {}

	public static void registerPayloads() {
		PayloadTypeRegistry.playS2C().register(VfxCuePayload.TYPE, VfxCuePayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(CharacterSelectionSyncPayload.TYPE, CharacterSelectionSyncPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SelectCharacterPayload.TYPE, SelectCharacterPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(CharacterAbilityPayload.TYPE, CharacterAbilityPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(AbilityCooldownPayload.TYPE, AbilityCooldownPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(CurseLinkOptionsPayload.TYPE, CurseLinkOptionsPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(SelectCurseLinkPayload.TYPE, SelectCurseLinkPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(BlackFlashFocusPayload.TYPE, BlackFlashFocusPayload.STREAM_CODEC);
		registerServerReceivers();
	}

	private static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.TYPE, (payload, context) ->
				context.server().execute(() -> CharacterSelectionManager.select(context.player(), JujutsuCharacter.byId(payload.characterId()))));
		ServerPlayNetworking.registerGlobalReceiver(CharacterAbilityPayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleCharacterAbility(context.player(), payload)));
		ServerPlayNetworking.registerGlobalReceiver(SelectCurseLinkPayload.TYPE, (payload, context) ->
				context.server().execute(() -> jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime.select(context.player(), payload.linkId())));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { CharacterSelectionManager.syncOnJoin(handler.player); jujutsu.mod.combat.BlackFlashFocus.sync(handler.player); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CharacterSelectionManager.disconnect(handler.player));
	}

	private static void handleCharacterAbility(ServerPlayer player, CharacterAbilityPayload payload) {
		CharacterAbility ability = CharacterAbility.byNetworkId(payload.abilityId());
		if (ability != null) {
			CharacterAbilityExecutor.tryCast(player, ability, true);
		}
	}

	public static int broadcastVfxCue(ServerLevel level, Vec3 center, double radius, VfxCue cue) {
		double radiusSqr = radius * radius;
		VfxCuePayload payload = new VfxCuePayload(cue);
		int sent = 0;
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	public static boolean sendVfxCue(ServerPlayer player, VfxCue cue) {
		if (!ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new VfxCuePayload(cue));
		return true;
	}

	/**
	 * Mirrors a started cooldown to its owner. The vessel is resolved here from the same source the
	 * server-side cooldown key uses, rather than named by the caller: the client suppresses input on
	 * {@code (vessel, slot)} and the server gates on it, so a caller naming the wrong vessel would
	 * silence one slot while refusing another.
	 */
	public static boolean sendAbilityCooldown(ServerPlayer player, CharacterAbility ability, int remainingTicks) {
		if (!ServerPlayNetworking.canSend(player, AbilityCooldownPayload.TYPE)) {
			return false;
		}
		JujutsuCharacter character = CharacterSelectionManager.selected(player);
		ServerPlayNetworking.send(player, new AbilityCooldownPayload(character.id(), ability.networkId(), Math.max(0, remainingTicks)));
		return true;
	}

}
