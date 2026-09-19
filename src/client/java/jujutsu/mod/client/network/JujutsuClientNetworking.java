package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.network.VfxCuePayload;
import jujutsu.mod.network.CurseLinkOptionsPayload;
import jujutsu.mod.client.gui.CurseLinkSelectionScreen;
import jujutsu.mod.network.BlackFlashFocusPayload;
import jujutsu.mod.client.character.ClientBlackFlashFocus;
import jujutsu.mod.client.character.ClientAbilityCooldowns;
import jujutsu.mod.client.tongue.TongueClientFx;
import jujutsu.mod.client.tongue.TongueClientState;
import jujutsu.mod.network.AbilityCooldownPayload;
import jujutsu.mod.network.MegumiTongueStatePayload;

public final class JujutsuClientNetworking {
	private JujutsuClientNetworking() {}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(VfxCuePayload.TYPE, (payload, context) ->
				context.client().execute(() -> VfxDirector.receive(payload.cue())));
		ClientPlayNetworking.registerGlobalReceiver(CharacterSelectionSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientCharacterSelectionManager.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CurseLinkOptionsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().setScreen(new CurseLinkSelectionScreen(payload.entries()))));
		ClientPlayNetworking.registerGlobalReceiver(BlackFlashFocusPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientBlackFlashFocus.apply(payload.focused())));
		ClientPlayNetworking.registerGlobalReceiver(AbilityCooldownPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientAbilityCooldowns.apply(payload)));
		// The tongue is the one client-authoritative physics in the mod: this payload is the whole
		// authoritative channel for it, and the particle line is a presentation of that same state
		// (not a VFX cue), so both are wired here with the receiver.
		ClientPlayNetworking.registerGlobalReceiver(MegumiTongueStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> TongueClientState.apply(payload)));
		TongueClientFx.register();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ClientCharacterSelectionManager.clear();
			ClientBlackFlashFocus.clear();
			ClientAbilityCooldowns.clear();
			TongueClientState.clear();
		});
	}
}
