package jujutsu.mod.client.render.megumi;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import jujutsu.mod.character.megumi.MegumiShikigamiPresentationPolicy;
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
	private static final String FEET_CONTROLLER = "megumi_nue_feet";
	/** Short blend, so the talons snap instead of easing. */
	private static final int FEET_TRANSITION_TICKS = 2;
	private static final RawAnimation IDLE = loop("animation.megumi_nue.idle");
	private static final RawAnimation FLY = loop("animation.megumi_nue.fly");
	/**
	 * The imported {@code flight_feet} clip animates the legs and feet only (four bones against the
	 * flight clip's ten) and {@code grab_feet} the two feet. Neither may own the body controller:
	 * doing so froze the wings, head and tail for the whole of fast travel — which is the entire dive.
	 * They ride their own layer instead, over the full-body clips, exactly like the dog's jaw.
	 */
	private static final RawAnimation FLIGHT_FEET = loop("animation.megumi_nue.flight_feet");
	private static final RawAnimation GRAB_FEET = loop("animation.megumi_nue.grab_feet");
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
		controllers.add(new AnimationController<MegumiNueGeoAnimatable>(FEET_CONTROLLER, FEET_TRANSITION_TICKS,
				this::feetAnimation));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}

	/**
	 * The body layer plays whole-body clips only: the imported {@code attack} clip animates the same
	 * ten bones as {@code fly}, so while the impact is live the dive pose holds, and when it ends the
	 * controller blends straight back into the flight clip instead of two layers fighting over bones.
	 * Fast travel keeps the flight cycle here — the legs come from the feet layer.
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

	/**
	 * The feet layer owns the legs and talons alone, so the wings keep beating through both fast travel
	 * and the dive: a paddling flight cycle while travelling, a talon clutch while the impact is live.
	 */
	private PlayState feetAnimation(AnimationTest<MegumiNueGeoAnimatable> state) {
		if (!(state.renderState() instanceof MegumiShikigamiRenderState nue)) {
			return PlayState.STOP;
		}
		if (nue.actionActive) {
			return state.setAndContinue(GRAB_FEET);
		}
		Vec3 velocity = state.getDataOrDefault(DataTickets.VELOCITY, Vec3.ZERO);
		if (velocity == null) {
			velocity = Vec3.ZERO;
		}
		if (nue.phase != MegumiShikigamiPresentationPolicy.Phase.RECALLING) {
			return MegumiShikigamiAnimationPolicy.isRunning(velocity.x, velocity.z)
					? state.setAndContinue(FLIGHT_FEET)
					: PlayState.STOP;
		}
		return PlayState.STOP;
	}

	private static RawAnimation rawAnimation(MegumiShikigamiAnimationPolicy.Clip clip) {
		return switch (clip) {
			case IDLE -> IDLE;
			case FLY -> FLY;
			case RUN -> FLY;
			case RISE -> FLY;
			case SINK -> IDLE;
			case ACTION -> ATTACK;
		};
	}

	private static RawAnimation loop(String name) {
		return RawAnimation.begin().thenLoop(name);
	}
}
