package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.vfx.todo.TodoVfxRecipes;
import jujutsu.mod.client.input.JujutsuKeybinds;
import jujutsu.mod.client.network.JujutsuClientNetworking;
import jujutsu.mod.client.particle.JujutsuClientParticles;
import jujutsu.mod.client.render.ProjectJjkNailRenderer;
import jujutsu.mod.client.render.nobara.doll.ProjectJjkStrawDollRenderer;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
import jujutsu.mod.client.rich.Initialization;
import jujutsu.mod.client.ui.neon.render.SdfPipelines;
import jujutsu.mod.character.nobara.projectjjk.ProjectJjkStrawDollItem;
import jujutsu.mod.registry.JujutsuEntities;

public class JujutsuModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ProjectJjkStrawDollItem.setRendererFactory(ProjectJjkStrawDollRenderer::provider);
		EntityRendererRegistry.register(JujutsuEntities.PROJECTJJK_NAIL, ProjectJjkNailRenderer::new);
		JujutsuClientParticles.registerFactories();
		VfxDirector.initialize();
		NobaraVfxRecipes.register();
		TodoVfxRecipes.register();
		JujutsuClientNetworking.registerReceivers();
		JujutsuKeybinds.register();
		// SDF panels for ClickGui (touching the field registers the pipeline).
		if (SdfPipelines.SDF_SHAPE == null) {
			throw new IllegalStateException("SDF pipeline failed to register");
		}
		// ClickGui MSDF type + slim Rich host (key N).
		MsdfFonts.bootstrap();
		Initialization.getInstance();
	}
}

