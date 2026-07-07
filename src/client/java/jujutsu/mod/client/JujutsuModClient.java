package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import jujutsu.mod.client.fx.CursedEnergyHud;
import jujutsu.mod.client.fx.HairpinPlaybackManager;
import jujutsu.mod.client.fx.HairpinScreenOverlay;
import jujutsu.mod.client.fx.HairpinWorldRenderer;
import jujutsu.mod.client.fx.NobaraNailFlightManager;
import jujutsu.mod.client.input.JujutsuKeybinds;
import jujutsu.mod.client.network.JujutsuClientNetworking;
import jujutsu.mod.client.particle.JujutsuClientParticles;
import jujutsu.mod.client.render.ProjectJjkNailRenderer;
import jujutsu.mod.registry.JujutsuEntities;

public class JujutsuModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(JujutsuEntities.PROJECTJJK_NAIL, ProjectJjkNailRenderer::new);
		JujutsuClientParticles.registerFactories();
		HairpinScreenOverlay.register();
		CursedEnergyHud.register();
		HairpinPlaybackManager.registerClientTick();
		NobaraNailFlightManager.registerClientTick();
		HairpinWorldRenderer.register();
		JujutsuClientNetworking.registerReceivers();
		JujutsuKeybinds.register();
	}
}
