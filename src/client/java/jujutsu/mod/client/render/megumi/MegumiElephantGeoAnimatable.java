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

/** Client-side animatable driving the imported Max Elephant render. */
public final class MegumiElephantGeoAnimatable implements GeoReplacedEntity {
	public static final MegumiElephantGeoAnimatable INSTANCE = new MegumiElephantGeoAnimatable();
	private static final String BASE_CONTROLLER = "megumi_elephant_base";
	private static final RawAnimation IDLE = loop("animation.megumi_max_elephant.idle");
	private static final RawAnimation WALK = loop("animation.megumi_max_elephant.walk");
	private static final RawAnimation RUN = loop("animation.megumi_max_elephant.run");
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.megumi_max_elephant.attack");
	/**
	 * The trunk jet: authored {@code loop:true} upstream, forced one-shot-and-hold here — while the
	 * jet is live the finished clip re-arms every tick, and when the action ends the controller
	 * blends straight back into locomotion. {@code RawAnimation} never reads the JSON loop flag.
	 */
	private static final RawAnimation SHOOT = RawAnimation.begin().thenPlay("animation.megumi_max_elephant.shoot");
	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	private MegumiElephantGeoAnimatable() {
		SingletonGeoAnimatable.registerSyncedAnimatable(this);
	}

	@Override
	public EntityType<?> getReplacingEntityType() {
		return JujutsuEntities.MEGUMI_MAX_ELEPHANT;
	}

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<MegumiElephantGeoAnimatable>(BASE_CONTROLLER, 4, this::baseAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/**
	 * The jet owns the whole body while it fires; a melee swing borrows the same controller only
	 * when no jet is running. Locomotion upgrades a policy walk to the run cycle at speed.
	 */
	private PlayState baseAnimation(AnimationTest<MegumiElephantGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState elephant)) {
			return state.setAndContinue(IDLE);
		}
		boolean moving = Boolean.TRUE.equals(state.getDataOrDefault(DataTickets.IS_MOVING, false));
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		boolean running = MegumiShikigamiAnimationPolicy.isRunning(velocity.x, velocity.z);
		MegumiShikigamiAnimationPolicy.Clip clip = MegumiShikigamiAnimationPolicy.elephant(
				elephant.phase, moving, elephant.actionActive, elephant.attackAnim);
		if (clip == MegumiShikigamiAnimationPolicy.Clip.ACTION) {
			RawAnimation action = elephant.actionActive ? SHOOT : ATTACK;
			if (state.isCurrentAnimation(action) && state.controller().hasAnimationFinished()) {
				state.resetCurrentAnimation();
			}
			return state.setAndContinue(action);
		}
		if (clip == MegumiShikigamiAnimationPolicy.Clip.FLY && running) {
			return state.setAndContinue(RUN);
		}
		return state.setAndContinue(rawAnimation(clip));
	}

	private static RawAnimation rawAnimation(MegumiShikigamiAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> IDLE;
			case FLY -> WALK;
			case RUN -> RUN;
			case RISE -> WALK;
			case SINK -> IDLE;
			case ACTION -> SHOOT;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
