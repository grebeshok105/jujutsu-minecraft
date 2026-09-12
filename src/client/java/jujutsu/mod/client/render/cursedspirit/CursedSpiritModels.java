package jujutsu.mod.client.render.cursedspirit;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import jujutsu.mod.JujutsuMod;
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

/**
 * The per-variant rig table: layer keys plus baked models, keyed by
 * {@link CursedSpiritVariant}.
 */
public final class CursedSpiritModels {
	private CursedSpiritModels() {
	}

	public static ModelLayerLocation layer(CursedSpiritVariant variant) {
		return new ModelLayerLocation(JujutsuMod.id("cursed_spirit/" + variant.id()), "main");
	}

	public static Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> bakeAll(
			EntityRendererProvider.Context context) {
		Map<CursedSpiritVariant, EntityModel<CursedSpiritRenderState>> rigs =
				new EnumMap<>(CursedSpiritVariant.class);
		rigs.put(CursedSpiritVariant.PROWLER,
				new CursedSpiritProwlerModel(context.bakeLayer(layer(CursedSpiritVariant.PROWLER))));
		rigs.put(CursedSpiritVariant.FLOATING_CURSE,
				new CursedSpiritFloatingCurseModel(context.bakeLayer(layer(CursedSpiritVariant.FLOATING_CURSE))));
		rigs.put(CursedSpiritVariant.GULBER,
				new CursedSpiritGulberModel(context.bakeLayer(layer(CursedSpiritVariant.GULBER))));
		rigs.put(CursedSpiritVariant.KELVIN,
				new CursedSpiritKelvinModel(context.bakeLayer(layer(CursedSpiritVariant.KELVIN))));
		rigs.put(CursedSpiritVariant.BUTCHER,
				new CursedSpiritButcherModel(context.bakeLayer(layer(CursedSpiritVariant.BUTCHER))));
		rigs.put(CursedSpiritVariant.GUZZLER,
				new CursedSpiritGuzzlerModel(context.bakeLayer(layer(CursedSpiritVariant.GUZZLER))));
		rigs.put(CursedSpiritVariant.BLUD,
				new CursedSpiritBludModel(context.bakeLayer(layer(CursedSpiritVariant.BLUD))));
		rigs.put(CursedSpiritVariant.WALKING_BED,
				new CursedSpiritWalkingBedModel(context.bakeLayer(layer(CursedSpiritVariant.WALKING_BED))));
		rigs.put(CursedSpiritVariant.WISTIVER,
				new CursedSpiritWistiverModel(context.bakeLayer(layer(CursedSpiritVariant.WISTIVER))));
		return rigs;
	}
}
