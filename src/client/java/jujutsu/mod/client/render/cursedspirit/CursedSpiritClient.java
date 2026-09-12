package jujutsu.mod.client.render.cursedspirit;

import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritBludModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritButcherModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritFloatingCurseModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritGulberModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritGuzzlerModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritKelvinModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritProwlerModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritWalkingBedModel;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritWistiverModel;
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
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.FLOATING_CURSE),
				CursedSpiritFloatingCurseModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.GULBER),
				CursedSpiritGulberModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.KELVIN),
				CursedSpiritKelvinModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.BUTCHER),
				CursedSpiritButcherModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.GUZZLER),
				CursedSpiritGuzzlerModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.BLUD),
				CursedSpiritBludModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.WALKING_BED),
				CursedSpiritWalkingBedModel::createBodyLayer);
		EntityModelLayerRegistry.registerModelLayer(CursedSpiritModels.layer(CursedSpiritVariant.WISTIVER),
				CursedSpiritWistiverModel::createBodyLayer);
		EntityRendererRegistry.register(JujutsuEntities.LESSER_CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.4f));
		EntityRendererRegistry.register(JujutsuEntities.CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.4f));
		EntityRendererRegistry.register(JujutsuEntities.GREATER_CURSED_SPIRIT,
				context -> new CursedSpiritRenderer(context, 0.7f));
	}
}
