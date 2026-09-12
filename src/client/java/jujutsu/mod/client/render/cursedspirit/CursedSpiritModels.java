package jujutsu.mod.client.render.cursedspirit;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.render.cursedspirit.model.CursedSpiritProwlerModel;
import jujutsu.mod.cursedspirit.CursedSpiritVariant;

/**
 * The per-variant rig table: layer keys plus baked models. Grows one entry per
 * ported variant; the renderer falls back to the prowler rig for variants whose
 * model has not landed yet (dev-time only — the rig contract test pins all nine).
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
		return rigs;
	}
}
