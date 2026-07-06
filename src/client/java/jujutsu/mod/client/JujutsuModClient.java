package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.network.JujutsuClientNetworking;

public class JujutsuModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		HairpinPlaybackManager.registerClientTick();
		JujutsuClientNetworking.registerReceivers();
	}
}
