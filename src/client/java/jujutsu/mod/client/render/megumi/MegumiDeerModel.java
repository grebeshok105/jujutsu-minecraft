package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Imported Round Deer model, animation and texture bindings. */
public final class MegumiDeerModel extends GeoModel<MegumiDeerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_deer");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_deer");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/megumi_deer.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiDeerGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
