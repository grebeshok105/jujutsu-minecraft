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

/** Client-side animatable driving the Dire Wolf replacement render for Divine Dogs. */
public final class MegumiDogGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiDogGeoAnimatable INSTANCE = new MegumiDogGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_dog_base";
	private static final String BITE_CONTROLLER = "megumi_dog_bite";
	/** Short blend, so the jaw snaps into a bite instead of easing into it. */
	private static final int BITE_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_divine_dog.idle");
	private static final RawAnimation WALK = loop("animation.megumi_divine_dog.walk");
	private static final RawAnimation SPRINT = loop("animation.megumi_divine_dog.sprint");
	private static final RawAnimation RISE = loop("animation.megumi_divine_dog.standup");
	private static final RawAnimation SINK = loop("animation.megumi_divine_dog.sitdown");
	/** The imported bite is a one-shot jaw clip; the base clips never touch that bone. */
	private static final RawAnimation BITE = RawAnimation.begin().thenPlay("animation.megumi_divine_dog.attack");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiDogGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_DIVINE_DOG;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiDogGeoAnimatable>(BASE_CONTROLLER, 5, this::baseAnimation));
		controllers.add(new AnimationController<MegumiDogGeoAnimatable>(BITE_CONTROLLER, BITE_TRANSITION_TICKS,
				this::biteAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiDogGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiDivineDogRenderState dog)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		boolean running = MegumiDogAnimationPolicy.isRunning(velocity.x, velocity.z);
		return state.setAndContinue(rawAnimation(MegumiDogAnimationPolicy.decide(dog.phase, moving, running)));
	}

	/**
	 * The bite rides its own controller: the imported clip animates the jaw only, so the legs keep their
	 * walk or sprint cycle while the dog is biting instead of freezing for the whole clip.
	 */
	private PlayState biteAnimation(AnimationTest<MegumiDogGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiDivineDogRenderState dog)) {
			return PlayState.STOP;
		}
		boolean swinging = MegumiDogAnimationPolicy.isAttacking(dog.attackAnim);
		boolean clipPlaying = state.isCurrentAnimation(BITE);
		boolean clipFinished = state.controller().hasAnimationFinished();
		if (MegumiDogAnimationPolicy.biteNeedsRestart(swinging, clipPlaying, clipFinished)) {
			state.resetCurrentAnimation();
		}
		return MegumiDogAnimationPolicy.biteOwnsJaw(swinging, clipPlaying, clipFinished)
				? state.setAndContinue(BITE)
				: PlayState.STOP;
	}

	private static RawAnimation rawAnimation(MegumiDogAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> IDLE;
			case WALK -> WALK;
			case SPRINT -> SPRINT;
			case RISE -> RISE;
			case SINK -> SINK;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
