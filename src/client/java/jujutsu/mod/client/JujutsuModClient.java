package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.fx.HairpinScreenOverlay;
import jujutsu.mod.client.fx.HairpinWorldRenderer;
import jujutsu.mod.client.network.JujutsuClientNetworking;
import jujutsu.mod.client.particle.JujutsuClientParticles;

public class JujutsuModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JujutsuClientParticles.registerFactories();
		HairpinScreenOverlay.register();
		HairpinPlaybackManager.registerClientTick();
		HairpinWorldRenderer.register();
		JujutsuClientNetworking.registerReceivers();
	}
}
