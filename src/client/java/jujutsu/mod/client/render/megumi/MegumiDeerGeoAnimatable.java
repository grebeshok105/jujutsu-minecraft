package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.EntityType;
import jujutsu.mod.registry.JujutsuEntities;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Client-side animatable for Round Deer's idle, walk and positive-energy pulse clips. */
public final class MegumiDeerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiDeerGeoAnimatable INSTANCE = new MegumiDeerGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_deer_base";
	private static final String ACTION_CONTROLLER = "megumi_deer_action";
	private static final RawAnimation IDLE = loop("animation.megumi_deer.idle");
	private static final RawAnimation WALK = loop("animation.megumi_deer.walk");
	private static final RawAnimation PULSE = RawAnimation.begin().thenPlay("animation.megumi_deer.pulse");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiDeerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_DEER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiDeerGeoAnimatable>(BASE_CONTROLLER, 4,
				this::baseAnimation));
		controllers.add(new AnimationController<MegumiDeerGeoAnimatable>(ACTION_CONTROLLER, 2,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiDeerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState deer)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiDeerAnimationPolicy.Clip clip = MegumiDeerAnimationPolicy.choose(
				deer.phase, moving, deer.actionActive);
		return switch (clip) {
			case IDLE -> state.setAndContinue(IDLE);
			case WALK -> state.setAndContinue(WALK);
			case RISE, SINK, PULSE -> PlayState.STOP;
		};
	}

	private PlayState actionAnimation(AnimationTest<MegumiDeerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState deer) || !deer.actionActive) {
			return PlayState.STOP;
		}
		if (state.isCurrentAnimation(PULSE) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(PULSE);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
