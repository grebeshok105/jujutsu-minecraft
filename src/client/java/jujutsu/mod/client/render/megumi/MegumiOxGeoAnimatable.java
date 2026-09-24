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

/** Client-side animatable for the imported Piercing Ox render. */
public final class MegumiOxGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiOxGeoAnimatable INSTANCE = new MegumiOxGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_ox_base";
	private static final String ACTION_CONTROLLER = "megumi_ox_action";
	private static final RawAnimation IDLE = loop("animation.megumi_ox.idle");
	private static final RawAnimation WALK = loop("animation.megumi_ox.walk");
	private static final RawAnimation WINDUP = RawAnimation.begin().thenPlay("animation.megumi_ox.windup");
	private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("animation.megumi_ox.charge");
	private static final RawAnimation IMPACT = RawAnimation.begin().thenPlay("animation.megumi_ox.impact");
	private static final RawAnimation RECOVER = RawAnimation.begin().thenPlay("animation.megumi_ox.recover");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiOxGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_OX;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiOxGeoAnimatable>(BASE_CONTROLLER, 4,
				this::baseAnimation));
		controllers.add(new AnimationController<MegumiOxGeoAnimatable>(ACTION_CONTROLLER, 2,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiOxGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState ox)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiOxAnimationPolicy.Clip clip = MegumiOxAnimationPolicy.choose(ox.phase, moving, actionTicks(ox));
		return switch (clip) {
			case IDLE -> state.setAndContinue(IDLE);
			case WALK -> state.setAndContinue(WALK);
			case WINDUP, CHARGE, IMPACT, RECOVER -> PlayState.STOP;
		};
	}

	private PlayState actionAnimation(AnimationTest<MegumiOxGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState ox)) {
			return PlayState.STOP;
		}
		MegumiOxAnimationPolicy.Clip clip = MegumiOxAnimationPolicy.choose(ox.phase,
				Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false)), actionTicks(ox));
		RawAnimation animation = switch (clip) {
			case WINDUP -> WINDUP;
			case CHARGE -> CHARGE;
			case IMPACT -> IMPACT;
			case RECOVER -> RECOVER;
			case IDLE, WALK -> null;
		};
		if (animation == null) {
			return PlayState.STOP;
		}
		if (state.isCurrentAnimation(animation) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(animation);
	}

	/** The Ox has no melee-swing layer, so the shared numeric field carries its synced action timer. */
	private static int actionTicks(MegumiShikigamiRenderState state) {
		return state.actionActive ? Math.round(state.attackAnim) : 0;
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
