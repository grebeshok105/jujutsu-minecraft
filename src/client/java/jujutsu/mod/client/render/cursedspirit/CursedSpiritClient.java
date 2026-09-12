package jujutsu.mod.client.render.cursedspirit;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritProwlerModel;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;
import jujutsu.mod.registry.JujutsuEntities;

/**
 * Client registration for the cursed-spirit render stack: model layers plus one
 * renderer instance per tier type (shadow radius per tier).
 */
public final class CursedSpiritClient {
	private CursedSpiritClient() {
	}

	public static void register() {
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.PROWLER),
				CursedSpiritProwlerModel::createBodyLayer);
		EntityRendererRegistry.register(JujutsuEntities.LESSER_CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.4f));
		EntityRendererRegistry.register(JujutsuEntities.CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.4f));
		EntityRendererRegistry.register(JujutsuEntities.GREATER_CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.7f));
	}
}
