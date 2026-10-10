package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Tiger Funeral model binding — geometry authored in Blockbench for this expansion. */
public final class MegumiTigerModel extends GeoModel<MegumiTigerGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_tiger");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_tiger");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/megumi_tiger.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiTigerGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
