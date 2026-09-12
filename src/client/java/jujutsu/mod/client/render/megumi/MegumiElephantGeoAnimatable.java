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
	private static final String ACTION_CONTROLLER = "megumi_elephant_action";
	/** Short blend, so the trunk snaps into the jet instead of easing into it. */
	private static final int ACTION_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_max_elephant.idle");
	private static final RawAnimation WALK = loop("animation.megumi_max_elephant.walk");
	private static final RawAnimation RUN = loop("animation.megumi_max_elephant.run");
	/**
	 * The imported {@code attack} clip animates the head alone (1 of the 12 geo bones). On the
	 * body controller it froze the legs, trunk and body for the whole swing; it rides the action
	 * layer instead, over the walk cycle, exactly like the Toad's swing.
	 */
	private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.megumi_max_elephant.attack");
	/**
	 * The imported {@code shoot} clip animates {@code trunk_1} + {@code trunk_2} only (2 of the 12
	 * geo bones). On the body controller the 48-tick jet froze the legs, body and head while only
	 * the trunk moved — the same defect fixed for the Nue's feet and the Toad's mouth. It rides
	 * the action layer instead, so the legs keep walking under the jet. Authored
	 * {@code loop:true} upstream, forced one-shot-and-hold here — while the jet is live the
	 * finished clip re-arms every tick, and when the action ends the layer stops outright.
	 * {@code RawAnimation} never reads the JSON loop flag.
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
		controllers.add(new AnimationController<MegumiElephantGeoAnimatable>(ACTION_CONTROLLER, ACTION_TRANSITION_TICKS,
				this::actionAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/**
	 * The body layer keeps locomotion only: while the jet fires or a swing lands, the legs keep
	 * their walk or run cycle underneath, and the action clips on the layer above own just the
	 * trunk and the head. The policy still reports the action (it drives the action layer), so an
	 * ACTION verdict here replays the travel cycle instead of interrupting it.
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
			return state.setAndContinue(moving ? (running ? RUN : WALK) : IDLE);
		}
		if (clip == MegumiShikigamiAnimationPolicy.Clip.FLY && running) {
			return state.setAndContinue(RUN);
		}
		return state.setAndContinue(rawAnimation(clip));
	}

	/**
	 * The action layer owns the trunk jet and the head swing: neither clip touches the legs, so
	 * the walk cycle underneath is never interrupted. The jet outranks a coincident swing — while
	 * the trunk is firing, its pose wins over the head toss.
	 */
	private PlayState actionAnimation(AnimationTest<MegumiElephantGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState elephant)) {
			return PlayState.STOP;
		}
		boolean jetting = elephant.actionActive;
		boolean swinging = MegumiShikigamiAnimationPolicy.isAttacking(elephant.attackAnim);
		if (!jetting && !swinging) {
			return PlayState.STOP;
		}
		RawAnimation oneShot = jetting ? SHOOT : ATTACK;
		if (state.isCurrentAnimation(oneShot) && state.controller().hasAnimationFinished()) {
			state.resetCurrentAnimation();
		}
		return state.setAndContinue(oneShot);
	}

	/**
	 * Body-layer mapping for the travel clips. The ACTION arm is unreachable from
	 * {@link #baseAnimation} (an ACTION verdict replays the travel cycle there while the action
	 * layer owns the jet and the swing) and stays only so the mapping is total over the policy.
	 */
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
