package jujutsu.mod;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.command.JujutsuCommands;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JujutsuMod implements ModInitializer {
	public static final String MOD_ID = "jujutsumod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		JujutsuParticles.register();
		JujutsuSounds.register();
		JujutsuNetworking.registerPayloads();
		JujutsuCommands.register();
		LOGGER.info("JujutsuMod initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
