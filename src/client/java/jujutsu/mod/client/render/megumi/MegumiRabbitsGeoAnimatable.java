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

/** Client-side animatable driving the imported Rabbit Escape render. */
public final class MegumiRabbitsGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiRabbitsGeoAnimatable INSTANCE = new MegumiRabbitsGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_rabbits_base";
	private static final String ACTION_CONTROLLER = "megumi_rabbits_action";
	/** The imported set ships no idle clip: the slow hop cycle doubles as the rest pose. */
	private static final RawAnimation WALK = loop("animation.megumi_rabbit.walk");
	private static final RawAnimation RUN = loop("animation.megumi_rabbit.run");
	/** The imported swing is a one-shot, opened by the server bump's synchronized swing state. */
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.megumi_rabbit.attack");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiRabbitsGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_RABBIT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiRabbitsGeoAnimatable>(BASE_CONTROLLER, 5, this::baseAnimation));
		controllers.add(new AnimationController<MegumiRabbitsGeoAnimatable>(ACTION_CONTROLLER, 2, this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	private PlayState baseAnimation(AnimationTest<MegumiRabbitsGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState rabbits)) {
			return state.setAndContinue(WALK);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		boolean running = MegumiShikigamiAnimationPolicy.isRunning(velocity.x, velocity.z);
		return state.setAndContinue(rawAnimation(
				MegumiShikigamiAnimationPolicy.rabbits(rabbits.phase, moving, running)));
	}

	/**
	 * The swing rides its own controller so the hop cycle never freezes while it plays. The render
	 * state is fed by the synchronized server swing, with the action timer as a compatible fallback.
	 */
	private PlayState actionAnimation(AnimationTest<MegumiRabbitsGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState rabbits)) {
			return PlayState.STOP;
		}
		if (!rabbits.actionActive) {
			return PlayState.STOP;
		}
		if (state.isCurrentAnimation(ATTACK) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(ATTACK);
	}

	private static RawAnimation rawAnimation(MegumiShikigamiAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> WALK;
			case FLY -> WALK;
			case RUN -> RUN;
			case RISE -> WALK;
			case SINK -> WALK;
			case ACTION -> ATTACK;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
