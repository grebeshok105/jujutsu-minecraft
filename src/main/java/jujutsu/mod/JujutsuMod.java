package jujutsu.mod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.resources.ResourceLocation;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkRitualRuntime;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollRuntime;
import jujutsu.mod.character.nobara.projectjjk.NailAnchorLifecycle;
import jujutsu.mod.character.nobara.projectjjk.NobaraHammerCombatRuntime;
import jujutsu.mod.character.nobara.projectjjk.NobaraActionGuard;
import jujutsu.mod.character.nobara.projectjjk.NailTrapRuntime;
import jujutsu.mod.character.nobara.projectjjk.SelfResonanceRuntime;
import jujutsu.mod.character.nobara.projectjjk.ResonantMomentum;
import jujutsu.mod.command.JujutsuCommands;
import jujutsu.mod.network.JujutsuNetworking;
import jujutsu.mod.registry.JujutsuDataComponents;
import jujutsu.mod.registry.JujutsuEntities;
import jujutsu.mod.registry.JujutsuItems;
import jujutsu.mod.registry.JujutsuParticles;
import jujutsu.mod.registry.JujutsuSounds;
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
		JujutsuDataComponents.register();
		JujutsuItems.register();
		JujutsuParticles.register();
		JujutsuSounds.register();
		JujutsuNetworking.registerPayloads();
		ProjectJjkRitualRuntime.register();
		ProjectJjkStrawDollRuntime.register();
		NailAnchorLifecycle.register();
		NobaraHammerCombatRuntime.register();
		NobaraActionGuard.register();
		SelfResonanceRuntime.register();
		NailTrapRuntime.register();
		ResonantMomentum.register();
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> CurseLinkRegistry.GLOBAL.clear());
		JujutsuCommands.register();
		ForcedBlackFlash.register();
		LOGGER.info("JujutsuMod initialized");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
