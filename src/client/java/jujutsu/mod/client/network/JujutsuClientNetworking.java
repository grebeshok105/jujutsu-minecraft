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
import jujutsu.mod.network.ResonantMomentumPayload;
import jujutsu.mod.client.character.ClientResonantMomentum;

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
		ClientPlayNetworking.registerGlobalReceiver(ResonantMomentumPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ClientResonantMomentum.apply(payload.remainingTicks())));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { ClientCharacterSelectionManager.clear(); ClientBlackFlashFocus.clear(); ClientResonantMomentum.clear(); });
	}
}
