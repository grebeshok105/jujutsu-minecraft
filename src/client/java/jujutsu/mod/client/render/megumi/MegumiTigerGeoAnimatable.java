package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animatable.processing.AnimationController;
import software.bernie.geckolib.animatable.processing.AnimationTest;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;
import jujutsu.mod.registry.JujutsuEntities;

/** Client-side BASE + ACTION controllers for Tiger Funeral's authored clips. */
public final class MegumiTigerGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiTigerGeoAnimatable INSTANCE = new MegumiTigerGeoAnimatable();
	public static final DataTicket<Integer> COMBO_STEP =
			DataTicket.create("jujutsumod_megumi_tiger_combo_step", Integer.class);
	private static final String BASE_CONTROLLER = "megumi_tiger_base";
	private static final String ACTION_CONTROLLER = "megumi_tiger_action";
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_tiger.idle");
	private static final RawAnimation WALK = loop("animation.megumi_tiger.walk");
	private static final RawAnimation WINDUP = play("animation.megumi_tiger.windup");
	private static final RawAnimation STRIKE_1 = play("animation.megumi_tiger.strike_1");
	private static final RawAnimation STRIKE_2 = play("animation.megumi_tiger.strike_2");
	private static final RawAnimation FINISHER = play("animation.megumi_tiger.finisher");
	private static final RawAnimation RECOVER = play("animation.megumi_tiger.recover");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiTigerGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_TIGER;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiTigerGeoAnimatable>(BASE_CONTROLLER, 4,
				this::baseAnimation));
		controllers.add(new AnimationController<MegumiTigerGeoAnimatable>(ACTION_CONTROLLER,
				ACTION_TRANSITION_TICKS, this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiTigerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState tiger)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		int step = state.getDataOrDefault(COMBO_STEP, 0);
		MegumiTigerAnimationPolicy.Clip clip = MegumiTigerAnimationPolicy.choose(
				tiger.phase, moving, tiger.actionActive, step);
		return switch (clip) {
			case IDLE -> state.setAndContinue(IDLE);
			case WALK -> state.setAndContinue(WALK);
			case WINDUP, STRIKE_1, STRIKE_2, FINISHER, RECOVER -> PlayState.STOP;
		};
	}

	private PlayState actionAnimation(AnimationTest<MegumiTigerGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState tiger)
				|| !tiger.actionActive) {
			return PlayState.STOP;
		}
		int step = state.getDataOrDefault(COMBO_STEP, 0);
		MegumiTigerAnimationPolicy.Clip clip = MegumiTigerAnimationPolicy.choose(
				tiger.phase, false, true, step);
		RawAnimation oneShot = switch (clip) {
			case WINDUP -> WINDUP;
			case STRIKE_1 -> STRIKE_1;
			case STRIKE_2 -> STRIKE_2;
			case FINISHER -> FINISHER;
			case RECOVER -> RECOVER;
			case IDLE, WALK -> null;
		};
		if (oneShot == null) {
			return PlayState.STOP;
		}
		if (state.isCurrentAnimation(oneShot) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(oneShot);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}

	private static RawAnimation play(String name) {
		return RawAnimation.begin().thenPlay(name);
	}
}
