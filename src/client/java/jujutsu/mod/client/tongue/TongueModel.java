package jujutsu.mod.client.tongue;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;
import jujutsu.mod.JujutsuMod;

/** Resource binding for the six tapered cubic tongue segments. */
public final class TongueModel extends GeoModel<TongueModel.Animatable> {
	public static final int SEGMENT_COUNT = 6;
	public static final ResourceLocation MODEL = JujutsuMod.id("megumi_tongue");
	public static final ResourceLocation TEXTURE =
			JujutsuMod.id("textures/entity/megumi_tongue.png");

	@Override
	public ResourceLocation getModelResource(GeoRenderState renderState) {
		return MODEL;
	}

	@Override
	public ResourceLocation getTextureResource(GeoRenderState renderState) {
		return TEXTURE;
	}

	@Override
	public ResourceLocation getAnimationResource(Animatable animatable) {
		return MODEL;
	}

	/** Standalone animatable kept for tools and future clip authoring; world rendering is dynamic. */
	public static final class Animatable implements GeoAnimatable {
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object relatedObject) {
			return relatedObject instanceof net.minecraft.world.entity.Entity entity
					? entity.tickCount : 0.0;
		}
	}
}
