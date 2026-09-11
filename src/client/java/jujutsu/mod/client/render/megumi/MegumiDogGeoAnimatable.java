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
	private static final RawAnimation IDLE = loop("animation.megumi_divine_dog.idle");
	private static final RawAnimation WALK = loop("animation.megumi_divine_dog.walk");
	private static final RawAnimation SPRINT = loop("animation.megumi_divine_dog.sprint");
	private static final RawAnimation ATTACK = loop("animation.megumi_divine_dog.attack");
	private static final RawAnimation RISE = loop("animation.megumi_divine_dog.standup");
	private static final RawAnimation SINK = loop("animation.megumi_divine_dog.sitdown");
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
		boolean attacking = MegumiDogAnimationPolicy.isAttacking(dog.attackAnim);
		return state.setAndContinue(rawAnimation(MegumiDogAnimationPolicy.decide(dog.phase, moving, running, attacking)));
	}

	private static RawAnimation rawAnimation(MegumiDogAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> IDLE;
			case WALK -> WALK;
			case SPRINT -> SPRINT;
			case ATTACK -> ATTACK;
			case RISE -> RISE;
			case SINK -> SINK;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
