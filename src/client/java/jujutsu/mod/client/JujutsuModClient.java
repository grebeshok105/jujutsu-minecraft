package jujutsu.mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import jujutsu.mod.client.vfx.VfxDirector;
import jujutsu.mod.client.vfx.nobara.NobaraVfxRecipes;
import jujutsu.mod.client.input.JujutsuKeybinds;
import jujutsu.mod.client.network.JujutsuClientNetworking;
import jujutsu.mod.client.particle.JujutsuClientParticles;
import jujutsu.mod.client.render.ProjectJjkNailRenderer;
import jujutsu.mod.client.render.nobara.doll.ProjectJjkStrawDollRenderer;
import jujutsu.mod.client.ui.msdf.MsdfFonts;
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
		JujutsuClientNetworking.registerReceivers();
		JujutsuKeybinds.register();
		// Register the neon-dashboard SDF pipeline (touching the field runs the static
		// RenderPipelines.register). Must happen before the first resource reload precompiles
		// pipelines so the shader is picked up.
		if (SdfPipelines.SDF_SHAPE == null) {
			throw new IllegalStateException("SDF pipeline failed to register");
		}
		// Modern menu MSDF pipeline (key N). Independent of neon dashboard (key V).
		MsdfFonts.bootstrap();
	}
}
