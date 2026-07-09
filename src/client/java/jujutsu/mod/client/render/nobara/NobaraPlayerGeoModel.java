package jujutsu.mod.client.render.nobara;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

public final class NobaraPlayerGeoModel extends GeoModel<NobaraPlayerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("geo/projectjjk/nobara_kugisaki.geo.json");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/projectjjk/entity/npcs/nobara_kugisaki.png");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("animations/projectjjk/npc.animation.json");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(NobaraPlayerGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
