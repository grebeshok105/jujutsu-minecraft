package jujutsu.mod.client.render.megumi;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;
import jujutsu.mod.JujutsuMod;
import jujutsu.mod.client.character.megumi.MegumiWingsState;

/** GeckoLib model binding for the standalone Nue wing layer. */
public final class MegumiWingsModel extends GeoModel<MegumiWingsModel.Animatable> {
	public static final DataTicket<Integer> PHASE =
			DataTicket.create("jujutsumod_megumi_wings_phase", Integer.class);

	private static final ResourceLocation MODEL = JujutsuMod.id("megumi_nue_wings");
	private static final ResourceLocation ANIMATIONS = JujutsuMod.id("megumi_nue_wings");
	private static final ResourceLocation TEXTURE =
			JujutsuMod.id("textures/entity/megumi_nue_wings.png");

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
		return ANIMATIONS;
	}

	/** Non-entity animatable used only to evaluate one wing rig per player render pass. */
	public static final class Animatable implements GeoAnimatable {
		private static final RawAnimation MATERIALIZE =
				RawAnimation.begin().thenPlay("animation.megumi_nue_wings.materialize");
		private static final RawAnimation FOLDED_IDLE =
				RawAnimation.begin().thenLoop("animation.megumi_nue_wings.folded_idle");
		private static final RawAnimation FLY =
				RawAnimation.begin().thenLoop("animation.megumi_nue_wings.fly");
		private static final RawAnimation FOLD =
				RawAnimation.begin().thenPlay("animation.megumi_nue_wings.fold");
		private static final RawAnimation UNFOLD =
				RawAnimation.begin().thenPlay("animation.megumi_nue_wings.unfold");
		private static final RawAnimation DISSOLVE =
				RawAnimation.begin().thenPlay("animation.megumi_nue_wings.dissolve");
		private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

		@Override
		public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
			controllers.add(new software.bernie.geckolib.animatable.processing.AnimationController<Animatable>(
					"megumi_wings", 0, this::animate));
		}

		@Override
		public AnimatableInstanceCache getAnimatableInstanceCache() {
			return cache;
		}

		@Override
		public double getTick(Object relatedObject) {
			return relatedObject instanceof net.minecraft.world.entity.Entity entity
					? entity.tickCount
					: 0.0;
		}

		private PlayState animate(AnimationTest<Animatable> state) {
			int wirePhase = state.getDataOrDefault(PHASE, MegumiWingsState.Phase.GROUND_FOLDED.wireValue());
			MegumiWingsState.Phase phase = MegumiWingsState.Phase.fromWire(wirePhase);
			if (phase == null) {
				return PlayState.STOP;
			}
			return state.setAndContinue(switch (phase) {
				case MATERIALIZING -> MATERIALIZE;
				case GROUND_FOLDED -> FOLDED_IDLE;
				case FLYING -> FLY;
				case FOLDING -> FOLD;
				case UNFOLDING -> UNFOLD;
				case DISSOLVING -> DISSOLVE;
			});
		}
	}
}
