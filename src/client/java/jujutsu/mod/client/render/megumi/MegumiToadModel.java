package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Imported Toad model binding (Sorcery Age ten-shadows set). */
public final class MegumiToadModel extends GeoModel<MegumiToadGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_toad");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_toad");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/megumi_toad.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiToadGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
