package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import jujutsu.mod.character.CharacterSelectionView;
import jujutsu.mod.client.character.ClientCharacterSelectionManager;
import jujutsu.mod.client.character.JujutsuCharacterClients;
import jujutsu.mod.client.vfx.VfxDirector;
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
		CharacterSelectionView.setClientLookup(ClientCharacterSelectionManager::characterOrNone);
		JujutsuClientParticles.registerFactories();
		VfxDirector.initialize();
		JujutsuCharacterClients.registerAll();
		DomainSphereDebug.register();
		BlackHoleDebug.register();
		ShikigamiSelectorDebug.register();
		CursedSpiritClient.register();
		CursedSpiritAura.register();
		JujutsuClientNetworking.registerReceivers();
		JujutsuKeybinds.register();
		if (SdfPipelines.SDF_SHAPE == null) {
			throw new IllegalStateException("SDF pipeline failed to register");
		}
		MsdfFonts.bootstrap();
		Initialization.getInstance();
		ClickGuiHud.register();
	}
}
