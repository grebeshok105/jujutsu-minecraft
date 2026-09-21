package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import jujutsu.mod.character.CharacterSelectionView;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.cursedincident.CursedIncidentClient;
import jujutsu.mod.client.vfx.blackhole.BlackHoleDebug;
import jujutsu.mod.client.vfx.domain.DomainSphereDebug;
import jujutsu.mod.client.vfx.domain.ShikigamiSelectorDebug;
import jujutsu.mod.client.input.JujutsuKeybinds;
import jujutsu.mod.client.render.cursedspirit.CursedSpiritAura;
import jujutsu.mod.client.render.cursedspirit.CursedSpiritClient;
import jujutsu.mod.client.network.JujutsuClientNetworking;
import jujutsu.mod.client.particle.JujutsuClientParticles;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.rich.Initialization;
import jujutsu.mod.client.rich.screens.clickgui.ClickGuiHud;
import jujutsu.mod.client.ui.neon.render.SdfPipelines;

public class JujutsuModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Lets shared code that runs on both sides ask which vessel a player is without this source set
		// ever touching a client class.
		CharacterSelectionView.setClientLookup(ClientCharacterSelectionManager::characterOrNone);
		JujutsuClientParticles.registerFactories();
		VfxDirector.initialize();
		CursedIncidentClient.register();
		// Each vessel installs its own renderers and VFX recipes. Must follow VfxDirector.initialize(),
		// because the recipes register into the director it builds.
		JujutsuCharacterClients.registerAll();
		// Debug-only effects are not vessel content: they register here so the vessel packs stay the
		// single owner of gameplay VFX (see the marker on DomainSphereDebug).
		DomainSphereDebug.register();
		BlackHoleDebug.register();
		ShikigamiSelectorDebug.register();
		CursedSpiritClient.register();
		CursedSpiritAura.register();
		JujutsuClientNetworking.registerReceivers();
		JujutsuKeybinds.register();
		// SDF panels for ClickGui (touching the field registers the pipeline).
		if (SdfPipelines.SDF_SHAPE == null) {
			throw new IllegalStateException("SDF pipeline failed to register");
		}
		// ClickGui MSDF type + slim Rich host (key N).
		MsdfFonts.bootstrap();
		Initialization.getInstance();
		ClickGuiHud.register();
	}
}

