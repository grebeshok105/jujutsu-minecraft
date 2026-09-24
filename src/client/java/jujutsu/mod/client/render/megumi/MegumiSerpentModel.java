package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Great Serpent model binding — geometry authored in Blockbench for this expansion. */
public final class MegumiSerpentModel extends GeoModel<MegumiSerpentGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_serpent");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_serpent");
	private static final ResourceLocation TEXTURE = JujutsuMod.id("textures/entity/megumi_serpent.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiSerpentGeoAnimatable animatable) {
		return ANIMATIONS;
	}
}
