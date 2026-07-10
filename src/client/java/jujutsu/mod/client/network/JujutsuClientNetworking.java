package jujutsu.mod.client.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.network.CharacterSelectionSyncPayload;
import jujutsu.mod.network.VfxCuePayload;
import jujutsu.mod.network.CurseLinkOptionsPayload;
import jujutsu.mod.client.gui.CurseLinkSelectionScreen;

public final class JujutsuClientNetworking {
	private JujutsuClientNetworking() {}

	public static void registerReceivers() {
		ClientPlayNetworking.registerGlobalReceiver(VfxCuePayload.TYPE, (payload, context) ->
				context.client().execute(() -> VfxDirector.receive(payload.cue())));
		ClientPlayNetworking.registerGlobalReceiver(CharacterSelectionSyncPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientCharacterSelectionManager.apply(payload)));
		ClientPlayNetworking.registerGlobalReceiver(CurseLinkOptionsPayload.TYPE, (payload, context) ->
				context.client().execute(() -> context.client().setScreen(new CurseLinkSelectionScreen(payload.entries()))));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientCharacterSelectionManager.clear());
	}
}
