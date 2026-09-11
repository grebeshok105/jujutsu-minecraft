package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.processing.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import jujutsu.mod.JujutsuMod;

/** Dire Wolf model binding for the Divine Dog GeckoLib renderer. */
public final class MegumiDivineDogModel extends GeoModel<MegumiDogGeoAnimatable> {
	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_divine_dog");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_divine_dog");
	private static final ResourceLocation WHITE = JujutsuMod.id("textures/entity/megumi_divine_dog_white.png");
	private static final ResourceLocation BLACK = JujutsuMod.id("textures/entity/megumi_divine_dog_black.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return renderState instanceof MegumiDivineDogRenderState dog && dog.blackVariant ? BLACK : WHITE;
	}

	@Override
	public ResourceLocation getAnimationResource(MegumiDogGeoAnimatable animatable) {
		return ANIMATIONS;
	}

	/**
	 * The Dire Wolf geometry is a rideable mount: {@code saddle}, {@code bridle} and {@code chests} carry
	 * the Mythic Mounts tack (bridle over the snout, saddle on the back, panniers on both flanks — their
	 * UV islands are leather-brown where the fur islands are neutral). Divine Dogs are not mounts, so
	 * those subtrees are hidden every frame. {@code chest} (singular) is the torso and stays visible.
	 */
	@Override
	public void setCustomAnimations(AnimationState<MegumiDogGeoAnimatable> animationState) {
		hide("saddle");
		hide("bridle");
		hide("chests");
	}

	private void hide(String boneName) {
		getBone(boneName).ifPresent(bone -> bone.setHidden(true));
	}
}
