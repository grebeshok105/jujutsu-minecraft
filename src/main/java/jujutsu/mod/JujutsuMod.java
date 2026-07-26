package jujutsu.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.character.CharacterAbilityCooldowns;
import jujutsu.mod.character.CharacterDefinition;
import jujutsu.mod.character.JujutsuCharacters;
import jujutsu.mod.character.CharacterCombatModifiers;
import jujutsu.mod.command.JujutsuCommands;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuAttachments;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
import jujutsu.mod.registry.JujutsuEffects;
import jujutsu.mod.combat.ForcedBlackFlash;
import jujutsu.mod.curse.CurseLinkRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JujutsuMod implements ModInitializer {
	public static final String MOD_ID = "jujutsumod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		JujutsuEntities.register();
		JujutsuAttachments.register();
		JujutsuDataComponents.register();
		JujutsuItems.register();
		JujutsuParticles.register();
		JujutsuSounds.register();
		JujutsuEffects.register();
		JujutsuNetworking.registerPayloads();
		CharacterAbilityCooldowns.register();
		CharacterCombatModifiers.register();
		// Each vessel brings its own listeners. This used to be twelve hand-listed calls, which meant a
		// new vessel had to edit mod init — the one shared file the definition seam was supposed to free.
		for (CharacterDefinition definition : JujutsuCharacters.all()) {
			definition.registerServerHooks();
		}
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CurseLinkRegistry.GLOBAL.clear());
		JujutsuCommands.register();
		ForcedBlackFlash.register();
		LOGGER.info("JujutsuMod initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
