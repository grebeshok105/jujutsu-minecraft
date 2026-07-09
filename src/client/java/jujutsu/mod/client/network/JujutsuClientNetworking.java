package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.network.VfxCuePayload;

public final class JujutsuClientNetworking {
	private JujutsuClientNetworking() {}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(VfxCuePayload.TYPE, (payload, context) ->
				context.client().execute(() -> VfxDirector.receive(payload.cue())));
		ClientPlayNetworking.registerGlobalReceiver(CharacterSelectionSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientCharacterSelectionManager.apply(payload)));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCharacterSelectionManager.clear());
	}
}
