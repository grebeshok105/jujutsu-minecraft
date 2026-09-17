package jujutsu.mod.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
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
import jujutsu.mod.character.JujutsuCharacters;
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
		PayloadTypeRegistry.playS2C().register(MegumiTongueStatePayload.TYPE, MegumiTongueStatePayload.STREAM_CODEC);
		registerServerReceivers();
	}

	private static void registerServerReceivers() {
		ServerPlayNetworking.registerGlobalReceiver(SelectCharacterPayload.TYPE, (payload, context) ->
				context.server().execute(() -> CharacterSelectionManager.select(context.player(), JujutsuCharacter.byId(payload.characterId()))));
		ServerPlayNetworking.registerGlobalReceiver(CharacterAbilityPayload.TYPE, (payload, context) ->
				context.server().execute(() -> handleCharacterAbility(context.player(), payload)));
		// Neutral intent: the client says which link was picked, and the player's own vessel decides what
		// that means. This used to name one vessel's runtime through an inline fully qualified name — the
		// shape that has no import line for a grep to find, and the defect tracked as E13.
		ServerPlayNetworking.registerGlobalReceiver(SelectCurseLinkPayload.TYPE, (payload, context) ->
				context.server().execute(() -> JujutsuCharacters.of(context.player()).selectCurseLink(context.player(), payload.linkId())));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> { CharacterSelectionManager.syncOnJoin(handler.player); jujutsu.mod.combat.BlackFlashFocus.sync(handler.player); });
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CharacterSelectionManager.disconnect(handler.player));
	}

	private static void handleCharacterAbility(ServerPlayer player, CharacterAbilityPayload payload) {
		CharacterAbility ability = CharacterAbility.byNetworkId(payload.abilityId());
		if (ability == null) {
			return;
		}
		// The menu applies a vessel switch locally and closes before the server has confirmed it. A key
		// press inside that window names the vessel the player had already left, and since a slot means a
		// different ability for each vessel, casting it would fire the wrong one. Refuse silently: the
		// player asked the vessel they can see for something it will do a tick later anyway.
		if (JujutsuCharacter.byId(payload.characterId()) != CharacterSelectionManager.selected(player)) {
			return;
		}
		CharacterAbilityExecutor.tryCast(player, ability, true);
	}

	public static int broadcastVfxCue(ServerLevel level, Vec3 center, double radius, VfxCue cue) {
		return broadcastVfxCue(level, center, radius, cue, player -> true);
	}

	/**
	 * Issue #80: curse cues go to an audience, not a radius. Curse call sites pass
	 * {@code CursePerception::perceives}; the old call shape keeps the allow-all filter.
	 */
	public static int broadcastVfxCue(ServerLevel level, Vec3 center, double radius, VfxCue cue,
			Predicate<ServerPlayer> audience) {
		VfxCuePayload payload = new VfxCuePayload(cue);
		int sent = 0;
		for (ServerPlayer player : recipients(level, center, radius, audience)) {
			if (ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
				sent++;
			}
		}
		return sent;
	}

	/**
	 * The pure audience selection behind the broadcast: in radius and accepted by the
	 * filter. Kept separate so GameTests can assert the audience directly — the
	 * {@code sent} counter stays 0 for placed victims (no negotiated channels, probe 1e)
	 * and can never serve as an oracle.
	 */
	public static List<ServerPlayer> recipients(ServerLevel level, Vec3 center, double radius,
			Predicate<ServerPlayer> audience) {
		double radiusSqr = radius * radius;
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (player.position().distanceToSqr(center) > radiusSqr) {
				continue;
			}
			if (!audience.test(player)) {
				continue;
			}
			out.add(player);
		}
		return out;
	}

	public static boolean sendVfxCue(ServerPlayer player, VfxCue cue) {
		if (!ServerPlayNetworking.canSend(player, VfxCuePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, new VfxCuePayload(cue));
		return true;
	}

	/**
	 * Pushes Toad's partial-tongue state to its owner (issue #108). The client owns the pull physics;
	 * this channel only tells it whether the tongue is anchored and where. Connection-null safe like
	 * {@link #sendAbilityCooldown}: a headless GameTest player has no channel to mirror to.
	 */
	public static boolean sendTongueState(ServerPlayer player, MegumiTongueStatePayload payload) {
		if (player.connection == null) {
			return false;
		}
		if (!ServerPlayNetworking.canSend(player, MegumiTongueStatePayload.TYPE)) {
			return false;
		}
		ServerPlayNetworking.send(player, payload);
		return true;
	}

	/**
	 * Mirrors a started cooldown to its owner. The vessel is resolved here from the same source the
	 * server-side cooldown key uses, rather than named by the caller: the client suppresses input on
	 * {@code (vessel, slot)} and the server gates on it, so a caller naming the wrong vessel would
	 * silence one slot while refusing another.
	 */
	public static boolean sendAbilityCooldown(ServerPlayer player, CharacterAbility ability, int remainingTicks) {
		// Fabric's canSend() asserts on the connection rather than reporting false, and a headless
		// GameTest player has none: nothing to mirror to, so the caller gets the same "not sent" answer.
		if (player.connection == null) {
			return false;
		}
		if (!ServerPlayNetworking.canSend(player, AbilityCooldownPayload.TYPE)) {
			return false;
		}
		JujutsuCharacter character = CharacterSelectionManager.selected(player);
		ServerPlayNetworking.send(player, new AbilityCooldownPayload(character.id(), ability.networkId(), Math.max(0, remainingTicks)));
		return true;
	}

}
