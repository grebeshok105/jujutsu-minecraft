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

/** Client-side animatable driving the imported Toad render. */
public final class MegumiToadGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiToadGeoAnimatable INSTANCE = new MegumiToadGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_toad_base";
	private static final String ACTION_CONTROLLER = "megumi_toad_action";
	/** Short blend, so the tongue snaps instead of easing. */
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation WALK = loop("animation.megumi_toad.walk");
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.megumi_toad.attack");
	/**
	 * The imported {@code tongue} clip animates the mouth bone alone. On the body controller it froze
	 * the whole toad — legs, arms and head — for the length of the strike; it rides its own layer
	 * instead, over the walk cycle, exactly like the dog's jaw.
	 */
	private static final RawAnimation TONGUE = RawAnimation.begin().thenPlay("animation.megumi_toad.tongue");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiToadGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_TOAD;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiToadGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
		controllers.add(new AnimationController<MegumiToadGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/**
	 * The body layer keeps the walk cycle and nothing else: while the tongue is out, a toad that is
	 * actually walking keeps its legs stepping under the strike, and one standing still holds the rest
	 * pose — a hop cycle running in place read as a bug. The imported swing clip shares the body and
	 * arms with this cycle, so it rides the action layer too. Rest is the model's bind pose: no idle
	 * clip is authored, so IDLE/SINK stop the controller outright.
	 */
	private PlayState baseAnimation(AnimationTest<MegumiToadGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState toad)) {
			return state.setAndContinue(WALK);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.toad(
				toad.phase, moving, toad.actionActive ? 1 : 0, toad.attackAnim);
		if (clip == MegumiShikigamiAnimationPolicy.Clip.ACTION) {
			return moving ? state.setAndContinue(WALK) : PlayState.STOP;
		}
		if (clip == MegumiShikigamiAnimationPolicy.Clip.IDLE
				|| clip == MegumiShikigamiAnimationPolicy.Clip.SINK) {
			return PlayState.STOP;
		}
		return state.setAndContinue(WALK);
	}

	/**
	 * The action layer owns the tongue and the swing: the tongue is the mouth alone, the swing the body
	 * and arms, and neither touches the legs, so the walk cycle underneath is never interrupted.
	 */
	private PlayState actionAnimation(AnimationTest<MegumiToadGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState toad)) {
			return PlayState.STOP;
		}
		boolean tongueOut = toad.actionActive;
		boolean swinging = MegumiShikigamiAnimationPolicy.isAttacking(toad.attackAnim);
		if (!tongueOut && !swinging) {
			return PlayState.STOP;
		}
		RawAnimation oneShot = tongueOut ? TONGUE : ATTACK;
		if (state.isCurrentAnimation(oneShot) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(oneShot);
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
