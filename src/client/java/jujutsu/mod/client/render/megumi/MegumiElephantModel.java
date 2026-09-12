package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Imported Max Elephant model binding (Sorcery Age ten-shadows set). */
public final class MegumiElephantModel extends GeoModel<MegumiElephantGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_max_elephant");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_max_elephant");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/megumi_max_elephant.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiElephantGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
