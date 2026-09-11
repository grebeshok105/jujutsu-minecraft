package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
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

/** Client-side animatable driving the imported Nue render. */
public final class MegumiNueGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiNueGeoAnimatable INSTANCE = new MegumiNueGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_nue_base";
	private static final RawAnimation IDLE = loop("animation.megumi_nue.idle");
	private static final RawAnimation FLY = loop("animation.megumi_nue.fly");
	private static final RawAnimation FLIGHT_FEET = loop("animation.megumi_nue.flight_feet");
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.megumi_nue.attack");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiNueGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_NUE;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiNueGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/**
	 * The imported Nue action clip animates the whole body, so it rides the same controller as the flight
	 * cycle: while the impact is live the dive pose holds, and when it ends the controller blends straight
	 * back into the flight clip instead of two layers fighting over the same bones.
	 */
	private PlayState baseAnimation(AnimationTest<MegumiNueGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState nue)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		boolean running = MegumiShikigamiAnimationPolicy.isRunning(velocity.x, velocity.z);
		MegumiShikigamiAnimationPolicy.Clip clip =
				MegumiShikigamiAnimationPolicy.nue(nue.phase, moving, running, nue.actionActive);
		if (clip == MegumiShikigamiAnimationPolicy.Clip.ACTION) {
			if (state.isCurrentAnimation(ATTACK) && state.controller().hasAnimationFinished()) {
				state.resetCurrentAnimation();
			}
			return state.setAndContinue(ATTACK);
		}
		return state.setAndContinue(rawAnimation(clip));
	}

	private static RawAnimation rawAnimation(MegumiShikigamiAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> IDLE;
			case FLY -> FLY;
			case RUN -> FLIGHT_FEET;
			case RISE -> FLIGHT_FEET;
			case SINK -> IDLE;
			case ACTION -> ATTACK;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
